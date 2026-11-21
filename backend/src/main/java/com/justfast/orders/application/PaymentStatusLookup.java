package com.justfast.orders.application;

import java.util.Collection;
import java.util.Map;

/**
 * Port implemented by the payments module so order views can show payment status without the orders module
 * depending on payment internals.
 */
public interface PaymentStatusLookup {

    /** Latest payment attempt per order id; orders without attempts are absent from the map. */
    Map<Long, PaymentSummary> latestFor(Collection<Long> orderIds);

    record PaymentSummary(String status, String provider, boolean open) {
    }
}
