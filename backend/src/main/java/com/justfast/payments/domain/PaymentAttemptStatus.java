package com.justfast.payments.domain;

public enum PaymentAttemptStatus {
    /** Recorded in our database; the provider session has not been created yet (or creation failed). */
    CREATED,
    /** Provider session exists; the customer may still pay. */
    PENDING,
    SUCCEEDED,
    FAILED,
    EXPIRED;

    public boolean isOpen() {
        return this == CREATED || this == PENDING;
    }
}
