package com.justfast.payments.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpHeaders;

/**
 * The only contract checkout depends on. Each provider adapter (sandbox, Stripe, ...) implements it, so checkout
 * and payment-outcome logic never touch a provider SDK or wire format.
 */
public interface PaymentGateway {

    /** Short provider id, also used in the webhook path: {@code /api/payments/webhooks/{provider}}. */
    String provider();

    /**
     * Creates a provider-hosted checkout session. Must be idempotent on {@link CheckoutSessionRequest#paymentAttemptId()}:
     * calling it twice for the same attempt returns the same session.
     */
    CheckoutSession createCheckoutSession(CheckoutSessionRequest request);

    /**
     * Verifies the webhook signature and translates the provider event.
     *
     * @throws InvalidWebhookSignatureException if the signature is missing, invalid, or too old
     */
    GatewayEvent parseWebhook(String payload, HttpHeaders headers);

    /** Asks the provider for the authoritative state of a session (used for reconciliation). */
    SessionStatus fetchSession(String providerSessionId);

    /** Closes an open session so it can no longer be paid. Must be safe to call on already-closed sessions. */
    void expireSession(String providerSessionId);

    record CheckoutSessionRequest(String paymentAttemptId, String orderNumber, BigDecimal amount, String currency,
                                  String customerEmail, String successUrl, String cancelUrl, Instant expiresAt) {
    }

    record CheckoutSession(String providerSessionId, String checkoutUrl) {
    }

    enum SessionState {
        OPEN,
        PAID,
        FAILED,
        EXPIRED
    }

    record SessionStatus(SessionState state, String paymentAttemptId, BigDecimal amount, String currency) {
    }
}
