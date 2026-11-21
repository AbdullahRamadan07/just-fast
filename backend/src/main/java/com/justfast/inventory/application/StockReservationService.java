package com.justfast.inventory.application;

import com.justfast.inventory.domain.ReservationStatus;
import com.justfast.inventory.domain.StockReservation;
import com.justfast.inventory.infrastructure.InventoryItemRepository;
import com.justfast.inventory.infrastructure.StockReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds, commits and releases stock for orders. Every method must join the caller's transaction so that
 * stock changes commit or roll back together with the order / payment state change that caused them.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class StockReservationService {

    private final InventoryItemRepository inventory;
    private final StockReservationRepository reservations;
    private final Clock clock;

    public StockReservationService(InventoryItemRepository inventory, StockReservationRepository reservations, Clock clock) {
        this.inventory = inventory;
        this.reservations = reservations;
        this.clock = clock;
    }

    public record Line(Long productId, String productName, int quantity) {
    }

    /**
     * Reserves every line or none (the caller's transaction rolls back on {@link InsufficientStockException}).
     * Lines are processed in product-id order so concurrent checkouts lock rows in the same order and cannot deadlock.
     */
    public void reserve(Long orderId, List<Line> lines) {
        Instant now = clock.instant();
        List<Line> ordered = lines.stream().sorted(Comparator.comparing(Line::productId)).toList();
        for (Line line : ordered) {
            if (inventory.tryReserve(line.productId(), line.quantity()) != 1) {
                throw new InsufficientStockException(line.productId(), line.productName());
            }
            reservations.save(new StockReservation(orderId, line.productId(), line.quantity(), now));
        }
    }

    /** Deducts the order's held units from stock. Idempotent: already-committed reservations are skipped. */
    public void commit(Long orderId) {
        Instant now = clock.instant();
        for (StockReservation reservation : activeReservations(orderId)) {
            if (inventory.commitReserved(reservation.getProductId(), reservation.getQuantity()) != 1) {
                throw new IllegalStateException("Inventory out of sync for product " + reservation.getProductId());
            }
            reservation.markCommitted(now);
        }
    }

    /** Returns the order's held units to available stock. Idempotent. */
    public void release(Long orderId) {
        Instant now = clock.instant();
        for (StockReservation reservation : activeReservations(orderId)) {
            if (inventory.releaseReserved(reservation.getProductId(), reservation.getQuantity()) != 1) {
                throw new IllegalStateException("Inventory out of sync for product " + reservation.getProductId());
            }
            reservation.markReleased(now);
        }
    }

    private List<StockReservation> activeReservations(Long orderId) {
        return reservations.findByOrderIdAndStatusOrderByProductIdAsc(orderId, ReservationStatus.ACTIVE);
    }
}
