package com.justfast.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Store-wide settings: the MVP sells in one currency and ships to one region.
 */
@Validated
@ConfigurationProperties(prefix = "store")
public record StoreProperties(
        @NotBlank String currency,
        @NotBlank String shippingCountry,
        @NotNull BigDecimal flatShippingFee,
        @NotBlank String publicBaseUrl,
        @NotNull Duration reservationTtl,
        long reconciliationIntervalMs,
        Admin admin) {

    public record Admin(String email, String password) {
    }

    public String publicUrl(String path) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        return base + path;
    }
}
