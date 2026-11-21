package com.justfast.checkout;

import com.justfast.checkout.OrderPlacementService.PlacedOrder;
import com.justfast.common.error.BusinessRuleException;
import com.justfast.common.error.NotFoundException;
import com.justfast.orders.domain.Order;
import com.justfast.orders.domain.OrderStatus;
import com.justfast.orders.infrastructure.OrderRepository;
import com.justfast.payments.application.PaymentSessionService;
import com.justfast.payments.application.PaymentSessionService.OrderPaymentInfo;
import com.justfast.payments.domain.PaymentAttempt;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Checkout orchestration:
 * <ol>
 *   <li>Idempotency: a repeated request with the same key returns the order it already created.</li>
 *   <li>{@link OrderPlacementService} reserves stock and records the pending order + payment attempt in one
 *       transaction.</li>
 *   <li>Outside that transaction, the provider-hosted checkout session is created (idempotently, keyed by the
 *       payment attempt id) and the customer is sent to it.</li>
 * </ol>
 */
@Service
public class CheckoutService {

    private final OrderPlacementService placement;
    private final PaymentSessionService payments;
    private final OrderRepository orders;
    private final Clock clock;

    public CheckoutService(OrderPlacementService placement, PaymentSessionService payments, OrderRepository orders,
                           Clock clock) {
        this.placement = placement;
        this.payments = payments;
        this.orders = orders;
        this.clock = clock;
    }

    public record CheckoutResult(Long orderId, String orderNumber, OrderStatus status, String checkoutUrl) {
    }

    public CheckoutResult checkout(Long userId, String email, Long addressId, String idempotencyKey) {
        Optional<Order> existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return resume(existing.get(), email);
        }
        PlacedOrder placed;
        try {
            placed = placement.place(userId, addressId, idempotencyKey);
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent request with the same key won the race; return its order instead of creating another.
            return orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .map(order -> resume(order, email))
                    .orElseThrow(() -> duplicate);
        }
        String url = payments.ensureCheckoutUrl(placed.paymentAttemptId(),
                new OrderPaymentInfo(placed.orderId(), placed.orderNumber(), email, placed.payBy())).orElse(null);
        return new CheckoutResult(placed.orderId(), placed.orderNumber(), OrderStatus.PENDING_PAYMENT, url);
    }

    /** "Continue to payment" for a customer's own unpaid order. */
    public CheckoutResult resumePayment(Long userId, String email, Long orderId) {
        Order order = orders.findById(orderId)
                .filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Order not found."));
        CheckoutResult result = resume(order, email);
        if (result.checkoutUrl() == null) {
            throw new BusinessRuleException("PAYMENT_NOT_AVAILABLE", "This order can no longer be paid.");
        }
        return result;
    }

    private CheckoutResult resume(Order order, String email) {
        String url = null;
        if (order.isAwaitingPayment() && !order.isReservationExpired(clock.instant())) {
            Optional<PaymentAttempt> open = payments.openAttempt(order.getId());
            if (open.isPresent()) {
                url = payments.ensureCheckoutUrl(open.get().getId(), new OrderPaymentInfo(order.getId(),
                        order.getOrderNumber(), email, order.getReservationExpiresAt())).orElse(null);
            }
        }
        return new CheckoutResult(order.getId(), order.getOrderNumber(), order.getStatus(), url);
    }
}
