package com.justfast.payments.application;

import com.justfast.orders.infrastructure.OrderRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically reconciles unpaid orders whose stock reservation expired. Safe to run on several replicas at once:
 * every change is applied under an order row lock and is idempotent.
 */
@Component
@Profile("!migrate & !test")
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private static final int BATCH_SIZE = 50;

    private final OrderRepository orders;
    private final PaymentReconciliationService reconciliation;
    private final Clock clock;

    public PaymentReconciliationJob(OrderRepository orders, PaymentReconciliationService reconciliation, Clock clock) {
        this.orders = orders;
        this.reconciliation = reconciliation;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${store.reconciliation-interval-ms:60000}", initialDelay = 15_000)
    public void run() {
        List<Long> expired = orders.findExpiredPendingOrderIds(clock.instant(), PageRequest.of(0, BATCH_SIZE));
        for (Long orderId : expired) {
            try {
                reconciliation.reconcileExpiredOrder(orderId);
            } catch (RuntimeException e) {
                log.error("Reconciliation failed for order {}", orderId, e);
            }
        }
    }
}
