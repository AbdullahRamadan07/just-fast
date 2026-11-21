package com.justfast.payments.gateway;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Timestamped HMAC-SHA256 signatures in the {@code t=<unix>,v1=<hex>} header format used by Stripe
 * (and reused by the sandbox provider). The signed payload is {@code "<t>.<raw body>"}.
 */
public final class WebhookSignature {

    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private WebhookSignature() {
    }

    public static String sign(String secret, String payload, Instant timestamp) {
        long t = timestamp.getEpochSecond();
        return "t=" + t + ",v1=" + hmacHex(secret, t + "." + payload);
    }

    public static void verify(String secret, String payload, String header, Instant now, Duration tolerance) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Webhook secret is not configured.");
        }
        if (header == null || header.isBlank()) {
            throw new InvalidWebhookSignatureException("Missing signature header.");
        }
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    throw new InvalidWebhookSignatureException("Malformed timestamp.");
                }
            } else if (kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new InvalidWebhookSignatureException("Malformed signature header.");
        }
        Duration age = Duration.between(Instant.ofEpochSecond(timestamp), now).abs();
        if (age.compareTo(tolerance) > 0) {
            throw new InvalidWebhookSignatureException("Signature timestamp outside tolerance.");
        }
        byte[] expected = hmacHex(secret, timestamp + "." + payload).getBytes(StandardCharsets.US_ASCII);
        for (String candidate : signatures) {
            if (MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.US_ASCII))) {
                return;
            }
        }
        throw new InvalidWebhookSignatureException("Signature mismatch.");
    }

    static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
