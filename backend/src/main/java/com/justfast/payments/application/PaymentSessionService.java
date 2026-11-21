package com.justfast.payments.application;

import com.justfast.common.config.StoreProperties;
import com.justfast.payments.domain.PaymentAttempt;
import com.justfast.payments.domain.PaymentAttemptStatus;
import com.justfast.payments.gateway.PaymentGateway;
import com.justfast.payments.gateway.PaymentGateway.CheckoutSession;
import com.justfast.payments.gateway.PaymentGateway.CheckoutSessionRequest;
import com.justfast.payments.infrastructure.PaymentAttemptRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates payment attempts (inside the checkout transaction) and hosted checkout sessions (outside any database
 * transaction, since it is a network call to the provider).
 */
@Service
public class PaymentSessionService {

    private final PaymentAttemptRepository attempts;
    private final PaymentGateway gateway;
    private final StoreProperties store;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentSessionService(PaymentAttemptRepository attempts, PaymentGateway gateway, StoreProperties store,
                                 TransactionTemplate tx, Clock clock) {
        this.attempts = attempts;
        this.gateway = gateway;
        this.store = store;
        this.tx = tx;
        this.clock = clock;
    }

    public record OrderPaymentInfo(Long orderId, String orderNumber, String customerEmail, Instant payBy) {
    }

    /** Records a new attempt for the order's total. Joins the checkout transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentAttempt createAttempt(Long orderId, BigDecimal amount, String currency) {
        return attempts.save(new PaymentAttempt(orderId, gateway.provider(), amount, currency, clock.instant()));
    }

    /** The most recent attempt for an order that the customer can still pay. */
    @Transactional(readOnly = true)
    public Optional<PaymentAttempt> openAttempt(Long orderId) {
        return attempts.findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(orderId,
                List.of(PaymentAttemptStatus.CREATED, PaymentAttemptStatus.PENDING));
    }

    /**
     * Returns the hosted checkout URL for the attempt, creating the provider session if needed. Safe to call
     * repeatedly: the provider call is idempotent on the attempt id, and only the first result is stored.
     */
    public Optional<String> ensureCheckoutUrl(String attemptId, OrderPaymentInfo info) {
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (attempt.getStatus() == PaymentAttemptStatus.PENDING) {
            return Optional.ofNullable(attempt.getCheckoutUrl());
        }
        if (attempt.getStatus() != PaymentAttemptStatus.CREATED) {
            return Optional.empty();
        }

        String returnUrl = store.publicUrl("/checkout/return?orderId=" + info.orderId());
        CheckoutSession session = gateway.createCheckoutSession(new CheckoutSessionRequest(
                attempt.getId(), info.orderNumber(), attempt.getAmount(), attempt.getCurrency(), info.customerEmail(),
                returnUrl, returnUrl + "&cancelled=true", info.payBy()));

        return tx.execute(status -> {
            PaymentAttempt fresh = attempts.findById(attemptId).orElseThrow();
            fresh.sessionCreated(session.providerSessionId(), session.checkoutUrl(), clock.instant());
            return fresh.getStatus() == PaymentAttemptStatus.PENDING
                    ? Optional.ofNullable(fresh.getCheckoutUrl())
                    : Optional.<String>empty();
        });
    }
}
