package com.justfast.orders.application;

import com.justfast.identity.domain.Address.AddressDetails;
import com.justfast.orders.application.PaymentStatusLookup.PaymentSummary;
import com.justfast.orders.domain.Order;
import com.justfast.orders.domain.OrderItem;
import com.justfast.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read models for orders (built inside a transaction so lazy collections are available). */
public final class OrderViews {

    private OrderViews() {
    }

    public record OrderSummary(Long id, String orderNumber, OrderStatus status, String paymentStatus,
                               BigDecimal total, String currency, int itemCount, Instant createdAt) {

        static OrderSummary of(Order order, PaymentSummary payment) {
            int count = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
            return new OrderSummary(order.getId(), order.getOrderNumber(), order.getStatus(),
                    payment == null ? null : payment.status(), order.getTotal(), order.getCurrency(), count,
                    order.getCreatedAt());
        }
    }

    public record OrderLineView(Long productId, String sku, String productName, BigDecimal unitPrice, int quantity,
                                BigDecimal lineTotal) {

        static OrderLineView of(OrderItem item) {
            return new OrderLineView(item.getProductId(), item.getSku(), item.getProductName(), item.getUnitPrice(),
                    item.getQuantity(), item.getLineTotal());
        }
    }

    public record OrderDetail(Long id, String orderNumber, Long customerId, OrderStatus status, String paymentStatus,
                              boolean canResumePayment, String currency, BigDecimal subtotal, BigDecimal shippingFee,
                              BigDecimal total, AddressDetails shippingAddress, List<OrderLineView> items,
                              Instant createdAt, Instant reservationExpiresAt, Instant paidAt, Instant shippedAt,
                              Instant deliveredAt, Instant cancelledAt) {

        static OrderDetail of(Order order, PaymentSummary payment, Instant now) {
            boolean canResume = order.isAwaitingPayment() && !order.isReservationExpired(now)
                    && (payment == null || payment.open());
            return new OrderDetail(order.getId(), order.getOrderNumber(), order.getUserId(), order.getStatus(),
                    payment == null ? null : payment.status(), canResume, order.getCurrency(), order.getSubtotal(),
                    order.getShippingFee(), order.getTotal(), order.getShippingAddress().toDetails(),
                    order.getItems().stream().map(OrderLineView::of).toList(), order.getCreatedAt(),
                    order.getReservationExpiresAt(), order.getPaidAt(), order.getShippedAt(), order.getDeliveredAt(),
                    order.getCancelledAt());
        }
    }
}
