package com.justfast.inventory.domain;

public enum ReservationStatus {
    /** Units are held for an unpaid order. */
    ACTIVE,
    /** Payment succeeded; units were deducted from on-hand stock. */
    COMMITTED,
    /** Payment failed or expired; units were returned to available stock. */
    RELEASED
}
