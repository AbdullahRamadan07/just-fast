package com.justfast.orders.infrastructure;

import com.justfast.orders.domain.Order;
import com.justfast.orders.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsByIdAndUserId(Long id, Long userId);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /** Serializes payment-outcome processing for one order (webhooks and reconciliation can race). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);

    @Query("""
            select o.id from CustomerOrder o
             where o.status = com.justfast.orders.domain.OrderStatus.PENDING_PAYMENT
               and o.reservationExpiresAt <= :now
             order by o.reservationExpiresAt asc
            """)
    List<Long> findExpiredPendingOrderIds(@Param("now") Instant now, Pageable pageable);

    long countByStatus(OrderStatus status);
}
