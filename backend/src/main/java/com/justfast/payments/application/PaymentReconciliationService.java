package com.justfast.payments.application;

import com.justfast.payments.application.PaymentOutcomeService.Outcome;
import com.justfast.payments.domain.PaymentAttempt;
import com.justfast.payments.gateway.PaymentGateway;
import com.justfast.payments.gateway.PaymentGateway.SessionState;
import com.justfast.payments.gateway.PaymentGateway.SessionStatus;
import com.justfast.payments.gateway.PaymentGatewayException;
import com.justfast.payments.infrastructure.PaymentAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves orders whose payment window closed without a definitive webhook: asks the provider for the truth,
 * closes still-open sessions, applies the outcome, and releases stock only when no attempt can still succeed.
 */
@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);

    private final PaymentAttemptRepository attempts;
    private final PaymentGateway gateway;
    private final PaymentOutcomeService outcomes;

    public PaymentReconciliationService(PaymentAttemptRepository attempts, PaymentGateway gateway,
                                        PaymentOutcomeService outcomes) {
        this.attempts = attempts;
        this.gateway = gateway;
        this.outcomes = outcomes;
    }

    public void reconcileExpiredOrder(Long orderId) {
        boolean unresolved = false;
        for (PaymentAttempt attempt : attempts.findByOrderIdOrderByCreatedAtAsc(orderId)) {
            if (!attempt.isOpen()) {
                continue;
            }
            try {
                unresolved |= !reconcileAttempt(attempt);
            } catch (PaymentGatewayException e) {
                unresolved = true;
                log.warn("Could not reconcile attempt {} for order {}: {}", attempt.getId(), orderId, e.getMessage());
            }
        }
        if (!unresolved) {
            outcomes.cancelIfNothingOpen(orderId);
        }
    }

    /** @return true when the attempt reached a final state */
    private boolean reconcileAttempt(PaymentAttempt attempt) {
        if (attempt.getProviderSessionId() == null) {
            // Session was never confirmed as created. If the provider did create it and it gets paid later, that late
            // success is routed to PAYMENT_REVIEW by PaymentOutcomeService.
            outcomes.apply(attempt.getId(), Outcome.EXPIRED, null, null);
            return true;
        }
        SessionStatus status = gateway.fetchSession(attempt.getProviderSessionId());
        if (status.state() == SessionState.OPEN) {
            gateway.expireSession(attempt.getProviderSessionId());
            // Re-read: the customer may have completed payment just before the session was closed.
            status = gateway.fetchSession(attempt.getProviderSessionId());
        }
        return switch (status.state()) {
            case PAID -> {
                outcomes.apply(attempt.getId(), Outcome.SUCCEEDED, status.amount(), status.currency());
                yield true;
            }
            case FAILED -> {
                outcomes.apply(attempt.getId(), Outcome.FAILED, null, null);
                yield true;
            }
            case EXPIRED -> {
                outcomes.apply(attempt.getId(), Outcome.EXPIRED, null, null);
                yield true;
            }
            case OPEN -> false;
        };
    }
}
