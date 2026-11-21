package com.justfast.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import com.justfast.common.money.Pricing.PricedLine;
import com.justfast.common.money.Pricing.Totals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingTest {

    @Test
    void totalsUseExactDecimalArithmetic() {
        Totals totals = Pricing.totals(List.of(
                new PricedLine(new BigDecimal("0.10"), 3),
                new PricedLine(new BigDecimal("19.99"), 2)), new BigDecimal("5.00"));

        assertThat(totals.subtotal()).isEqualByComparingTo("40.28");
        assertThat(totals.shippingFee()).isEqualByComparingTo("5.00");
        assertThat(totals.total()).isEqualByComparingTo("45.28");
        assertThat(totals.total().scale()).isEqualTo(2);
    }

    @Test
    void emptyCartHasNoShippingFee() {
        Totals totals = Pricing.totals(List.of(), new BigDecimal("5.00"));
        assertThat(totals.total()).isEqualByComparingTo("0.00");
    }
}
