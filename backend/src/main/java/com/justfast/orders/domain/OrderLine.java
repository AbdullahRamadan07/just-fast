package com.justfast.orders.domain;

import com.justfast.common.money.Pricing.PricedLine;
import java.math.BigDecimal;

/** A priced line used to build an order. */
public record OrderLine(Long productId, String sku, String productName, BigDecimal unitPrice, int quantity) {

    public OrderLine {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
    }

    public PricedLine priced() {
        return new PricedLine(unitPrice, quantity);
    }

    public BigDecimal lineTotal() {
        return priced().lineTotal();
    }
}
