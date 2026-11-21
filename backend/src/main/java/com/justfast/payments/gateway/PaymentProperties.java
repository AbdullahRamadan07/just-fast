package com.justfast.payments.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(String provider, Sandbox sandbox, Stripe stripe) {

    public record Sandbox(String webhookSecret, String webhookUrl) {
    }

    public record Stripe(String apiKey, String webhookSecret, String apiBaseUrl) {
    }
}
