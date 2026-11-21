package com.justfast.orders.domain;

/**
 * Order (fulfillment) lifecycle. Payment-attempt status is tracked separately on PaymentAttempt so that
 * payment retries and shipping updates evolve independently.
 */
public enum OrderStatus {
    /** Stock is reserved; waiting for a successful payment. */
    PENDING_PAYMENT,
    /** Paid; stock committed; ready to ship. */
    PAID,
    SHIPPED,
    DELIVERED,
    /** Payment failed or expired; reserved stock was released. Never fulfilled. */
    CANCELLED,
    /**
     * A payment succeeded for an order that could not be fulfilled automatically (for example, it arrived after
     * the reservation expired, or the amount did not match). Requires manual reconciliation; never auto-fulfilled.
     */
    PAYMENT_REVIEW
}
