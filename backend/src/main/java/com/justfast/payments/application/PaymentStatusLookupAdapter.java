package com.justfast.payments.application;

import com.justfast.orders.application.PaymentStatusLookup;
import com.justfast.payments.domain.PaymentAttempt;
import com.justfast.payments.infrastructure.PaymentAttemptRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentStatusLookupAdapter implements PaymentStatusLookup {

    private final PaymentAttemptRepository attempts;

    public PaymentStatusLookupAdapter(PaymentAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, PaymentSummary> latestFor(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PaymentAttempt> latest = new HashMap<>();
        Comparator<PaymentAttempt> byCreated = Comparator.comparing(PaymentAttempt::getCreatedAt);
        for (PaymentAttempt attempt : attempts.findByOrderIdIn(orderIds)) {
            latest.merge(attempt.getOrderId(), attempt, (a, b) -> byCreated.compare(a, b) >= 0 ? a : b);
        }
        Map<Long, PaymentSummary> result = new HashMap<>();
        latest.forEach((orderId, a) ->
                result.put(orderId, new PaymentSummary(a.getStatus().name(), a.getProvider(), a.isOpen())));
        return result;
    }
}
