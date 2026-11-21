package com.justfast.payments.application;

import com.justfast.payments.application.PaymentOutcomeService.Outcome;
import com.justfast.payments.application.PaymentOutcomeService.Result;
import com.justfast.payments.domain.ProcessedWebhookEvent;
import com.justfast.payments.gateway.GatewayEvent;
import com.justfast.payments.infrastructure.ProcessedWebhookEventRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent webhook handling: the processed-event record and the resulting payment/order/stock changes are
 * written in the same transaction. A duplicate delivery either sees the record and returns, or (if it races)
 * fails the unique constraint and rolls back without side effects.
 */
@Service
public class WebhookProcessor {

    private final ProcessedWebhookEventRepository processedEvents;
    private final PaymentOutcomeService outcomes;
    private final Clock clock;

    public WebhookProcessor(ProcessedWebhookEventRepository processedEvents, PaymentOutcomeService outcomes,
                            Clock clock) {
        this.processedEvents = processedEvents;
        this.outcomes = outcomes;
        this.clock = clock;
    }

    public enum Disposition {
        PROCESSED,
        DUPLICATE,
        IGNORED
    }

    /**
     * @throws org.springframework.dao.DataIntegrityViolationException when a concurrent duplicate won the race
     */
    @Transactional
    public Disposition process(String provider, GatewayEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("Event id missing");
        }
        if (processedEvents.existsByProviderAndEventId(provider, event.eventId())) {
            return Disposition.DUPLICATE;
        }
        processedEvents.saveAndFlush(new ProcessedWebhookEvent(provider, event.eventId(), event.rawType(),
                clock.instant()));

        Outcome outcome = switch (event.type()) {
            case PAYMENT_SUCCEEDED -> Outcome.SUCCEEDED;
            case PAYMENT_FAILED -> Outcome.FAILED;
            case SESSION_EXPIRED -> Outcome.EXPIRED;
            case IGNORED -> null;
        };
        if (outcome == null) {
            return Disposition.IGNORED;
        }
        Result result = outcomes.apply(event.paymentAttemptId(), outcome, event.amount(), event.currency());
        return result == Result.UNKNOWN_ATTEMPT ? Disposition.IGNORED : Disposition.PROCESSED;
    }
}
