package com.justfast.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure order-total arithmetic. All monetary values are {@link BigDecimal} with two decimals,
 * matching the DECIMAL(12,2) columns.
 */
public final class Pricing {

    private Pricing() {
    }

    public record PricedLine(BigDecimal unitPrice, int quantity) {

        public BigDecimal lineTotal() {
            return money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }
    }

    public record Totals(BigDecimal subtotal, BigDecimal shippingFee, BigDecimal total) {
    }

    public static Totals totals(List<PricedLine> lines, BigDecimal flatShippingFee) {
        BigDecimal subtotal = money(lines.stream().map(PricedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal shipping = lines.isEmpty() ? money(BigDecimal.ZERO) : money(flatShippingFee);
        return new Totals(subtotal, shipping, money(subtotal.add(shipping)));
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
