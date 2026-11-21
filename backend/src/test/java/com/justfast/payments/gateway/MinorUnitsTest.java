package com.justfast.payments.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MinorUnitsTest {

    @Test
    void convertsTwoDecimalCurrencies() {
        assertThat(MinorUnits.toMinor(new BigDecimal("84.90"), "USD")).isEqualTo(8490L);
        assertThat(MinorUnits.fromMinor(8490L, "usd")).isEqualByComparingTo("84.90");
    }

    @Test
    void convertsZeroDecimalCurrencies() {
        assertThat(MinorUnits.toMinor(new BigDecimal("500"), "JPY")).isEqualTo(500L);
    }

    @Test
    void refusesToRoundSilently() {
        assertThatThrownBy(() -> MinorUnits.toMinor(new BigDecimal("1.005"), "USD"))
                .isInstanceOf(ArithmeticException.class);
    }
}
