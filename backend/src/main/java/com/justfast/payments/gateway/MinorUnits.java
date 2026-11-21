package com.justfast.payments.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/** Converts between major-unit {@link BigDecimal} amounts and provider minor units (e.g. cents). */
public final class MinorUnits {

    private MinorUnits() {
    }

    public static long toMinor(BigDecimal amount, String currency) {
        int digits = fractionDigits(currency);
        return amount.setScale(digits, RoundingMode.UNNECESSARY).movePointRight(digits).longValueExact();
    }

    public static BigDecimal fromMinor(long minor, String currency) {
        int digits = fractionDigits(currency);
        return BigDecimal.valueOf(minor).movePointLeft(digits).setScale(Math.max(digits, 2), RoundingMode.UNNECESSARY);
    }

    private static int fractionDigits(String currency) {
        int digits = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
        return Math.max(digits, 0);
    }
}
