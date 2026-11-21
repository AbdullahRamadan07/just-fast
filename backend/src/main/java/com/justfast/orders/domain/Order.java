package com.justfast.orders.domain;

import com.justfast.common.error.BusinessRuleException;
import com.justfast.common.money.Pricing;
import com.justfast.common.money.Pricing.Totals;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "reservation_expires_at", nullable = false)
    private Instant reservationExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    /** Places a new order awaiting payment. Totals are computed here from the priced lines. */
    public static Order place(String orderNumber, Long userId, String idempotencyKey, String currency,
                              List<OrderLine> lines, BigDecimal flatShippingFee, ShippingAddress shippingAddress,
                              Instant now, Instant reservationExpiresAt) {
        if (lines.isEmpty()) {
            throw new BusinessRuleException("CART_EMPTY", "Your cart is empty.");
        }
        Order order = new Order();
        order.orderNumber = orderNumber;
        order.userId = userId;
        order.idempotencyKey = idempotencyKey;
        order.currency = currency;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.shippingAddress = shippingAddress;
        order.createdAt = now;
        order.updatedAt = now;
        order.reservationExpiresAt = reservationExpiresAt;
        for (OrderLine line : lines) {
            order.items.add(new OrderItem(order, line, currency));
        }
        Totals totals = Pricing.totals(lines.stream().map(OrderLine::priced).toList(), flatShippingFee);
        order.subtotal = totals.subtotal();
        order.shippingFee = totals.shippingFee();
        order.total = totals.total();
        return order;
    }

    public void markPaid(Instant now) {
        transition(Set.of(OrderStatus.PENDING_PAYMENT), OrderStatus.PAID, now);
        this.paidAt = now;
    }

    public void cancelUnpaid(Instant now) {
        transition(Set.of(OrderStatus.PENDING_PAYMENT), OrderStatus.CANCELLED, now);
        this.cancelledAt = now;
    }

    /** A payment that cannot be applied automatically; stock is not committed and the order is never auto-shipped. */
    public void flagForPaymentReview(Instant now) {
        transition(Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, OrderStatus.PAYMENT_REVIEW),
                OrderStatus.PAYMENT_REVIEW, now);
    }

    public void ship(Instant now) {
        transition(Set.of(OrderStatus.PAID), OrderStatus.SHIPPED, now);
        this.shippedAt = now;
    }

    public void deliver(Instant now) {
        transition(Set.of(OrderStatus.SHIPPED), OrderStatus.DELIVERED, now);
        this.deliveredAt = now;
    }

    public boolean isAwaitingPayment() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean isReservationExpired(Instant now) {
        return !now.isBefore(reservationExpiresAt);
    }

    public boolean isOwnedBy(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }

    private void transition(Set<OrderStatus> allowedFrom, OrderStatus target, Instant now) {
        if (!allowedFrom.contains(status)) {
            throw new BusinessRuleException("INVALID_ORDER_TRANSITION",
                    "Order " + orderNumber + " cannot move from " + status + " to " + target + ".");
        }
        this.status = target;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
