package com.justfast.payments.infrastructure;

import com.justfast.payments.domain.PaymentAttempt;
import com.justfast.payments.domain.PaymentAttemptStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {

    List<PaymentAttempt> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<PaymentAttempt> findByOrderIdIn(Collection<Long> orderIds);

    Optional<PaymentAttempt> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(Long orderId,
                                                                               Collection<PaymentAttemptStatus> statuses);

    @Query("select a.orderId from PaymentAttempt a where a.id = :id")
    Optional<Long> findOrderIdById(@Param("id") String id);

    @Query("select count(a) from PaymentAttempt a where a.orderId = :orderId and a.status in :statuses")
    long countByOrderIdAndStatusIn(@Param("orderId") Long orderId,
                                   @Param("statuses") Collection<PaymentAttemptStatus> statuses);
}
