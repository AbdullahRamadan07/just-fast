package com.justfast.orders.application;

import com.justfast.common.error.NotFoundException;
import com.justfast.inventory.application.StockReservationService;
import com.justfast.orders.domain.Order;
import com.justfast.orders.infrastructure.OrderRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order-side effects of payment outcomes. Each transition moves order status and stock together inside the
 * caller's transaction, so a crash can never leave a paid order with released stock (or vice versa).
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrderPaymentService {

    private final OrderRepository orders;
    private final StockReservationService reservations;
    private final Clock clock;

    public OrderPaymentService(OrderRepository orders, StockReservationService reservations, Clock clock) {
        this.orders = orders;
        this.reservations = reservations;
        this.clock = clock;
    }

    /** Row-locks the order so concurrent webhooks / reconciliation for it are applied one at a time. */
    public Order lock(Long orderId) {
        return orders.lockById(orderId).orElseThrow(() -> new NotFoundException("Order not found."));
    }

    public void confirmPaid(Order order) {
        order.markPaid(clock.instant());
        reservations.commit(order.getId());
    }

    public void cancelUnpaid(Order order) {
        order.cancelUnpaid(clock.instant());
        reservations.release(order.getId());
    }

    /** Keeps (or puts) the order out of fulfillment until someone reconciles the payment manually. */
    public void flagForReview(Order order) {
        order.flagForPaymentReview(clock.instant());
    }
}
