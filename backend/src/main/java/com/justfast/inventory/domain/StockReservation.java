package com.justfast.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockReservation() {
    }

    public StockReservation(Long orderId, Long productId, int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive.");
        }
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }

    public void markCommitted(Instant now) {
        transition(ReservationStatus.COMMITTED, now);
    }

    public void markReleased(Instant now) {
        transition(ReservationStatus.RELEASED, now);
    }

    private void transition(ReservationStatus target, Instant now) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation " + id + " is already " + status);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}
