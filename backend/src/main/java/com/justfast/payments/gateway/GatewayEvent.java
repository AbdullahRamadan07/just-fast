package com.justfast.payments.gateway;

import java.math.BigDecimal;

/**
 * A verified, provider-neutral payment event.
 *
 * @param eventId          provider's unique event id (deduplicated per provider)
 * @param type             normalized outcome
 * @param paymentAttemptId our stable attempt id echoed back by the provider
 * @param amount           amount the provider reports, in major units
 */
public record GatewayEvent(String eventId, Type type, String rawType, String paymentAttemptId,
                           String providerSessionId, BigDecimal amount, String currency) {

    public enum Type {
        PAYMENT_SUCCEEDED,
        PAYMENT_FAILED,
        SESSION_EXPIRED,
        /** Verified but irrelevant to us; acknowledged and ignored. */
        IGNORED
    }

    public static GatewayEvent ignored(String eventId, String rawType) {
        return new GatewayEvent(eventId, Type.IGNORED, rawType, null, null, null, null);
    }
}
