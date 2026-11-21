package com.justfast.inventory.infrastructure;

import com.justfast.inventory.domain.ReservationStatus;
import com.justfast.inventory.domain.StockReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderIdAndStatusOrderByProductIdAsc(Long orderId, ReservationStatus status);

    List<StockReservation> findByOrderId(Long orderId);
}
