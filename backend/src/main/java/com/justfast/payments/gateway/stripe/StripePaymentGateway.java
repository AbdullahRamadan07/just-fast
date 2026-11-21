package com.justfast.payments.gateway.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justfast.payments.gateway.GatewayEvent;
import com.justfast.payments.gateway.InvalidWebhookSignatureException;
import com.justfast.payments.gateway.MinorUnits;
import com.justfast.payments.gateway.PaymentGateway;
import com.justfast.payments.gateway.PaymentGatewayException;
import com.justfast.payments.gateway.PaymentProperties;
import com.justfast.payments.gateway.WebhookSignature;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Stripe Checkout adapter using Stripe's REST API directly (no SDK on the classpath). Card data is collected by
 * Stripe's hosted page; this service only ever sees session ids and amounts.
 * Use a {@code sk_test_...} key and the Stripe CLI / dashboard webhook secret for sandbox testing.
 */
@Component
@ConditionalOnProperty(prefix = "payments", name = "provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "stripe";
    private static final Duration MIN_SESSION_LIFETIME = Duration.ofMinutes(31);

    private final RestClient client;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StripePaymentGateway(PaymentProperties properties, RestClient.Builder builder, ObjectMapper objectMapper,
                                Clock clock) {
        PaymentProperties.Stripe stripe = properties.stripe();
        if (stripe == null || stripe.apiKey() == null || stripe.apiKey().isBlank()
                || stripe.webhookSecret() == null || stripe.webhookSecret().isBlank()) {
            throw new IllegalStateException("STRIPE_API_KEY and STRIPE_WEBHOOK_SECRET must be set when PAYMENT_PROVIDER=stripe");
        }
        this.client = builder.baseUrl(stripe.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + stripe.apiKey())
                .build();
        this.webhookSecret = stripe.webhookSecret();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutSessionRequest request) {
        Instant minExpiry = clock.instant().plus(MIN_SESSION_LIFETIME);
        Instant expiresAt = request.expiresAt().isAfter(minExpiry) ? request.expiresAt() : minExpiry;

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", request.successUrl());
        form.add("cancel_url", request.cancelUrl());
        form.add("client_reference_id", request.paymentAttemptId());
        form.add("metadata[payment_attempt_id]", request.paymentAttemptId());
        form.add("metadata[order_number]", request.orderNumber());
        form.add("payment_intent_data[metadata][payment_attempt_id]", request.paymentAttemptId());
        form.add("expires_at", Long.toString(expiresAt.getEpochSecond()));
        if (request.customerEmail() != null) {
            form.add("customer_email", request.customerEmail());
        }
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", request.currency().toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]",
                Long.toString(MinorUnits.toMinor(request.amount(), request.currency())));
        form.add("line_items[0][price_data][product_data][name]", "Just Fast order " + request.orderNumber());

        try {
            JsonNode session = client.post()
                    .uri("/v1/checkout/sessions")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    // Provider-side idempotency: retries for the same attempt return the same session.
                    .header("Idempotency-Key", "checkout-session-" + request.paymentAttemptId())
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (session == null || session.path("id").isMissingNode() || session.path("url").isMissingNode()) {
                throw new PaymentGatewayException("Stripe returned an incomplete checkout session.");
            }
            return new CheckoutSession(session.path("id").asText(), session.path("url").asText());
        } catch (RestClientException e) {
            throw new PaymentGatewayException("Stripe checkout session creation failed", e);
        }
    }

    @Override
    public GatewayEvent parseWebhook(String payload, HttpHeaders headers) {
        WebhookSignature.verify(webhookSecret, payload, headers.getFirst("Stripe-Signature"), clock.instant(),
                WebhookSignature.DEFAULT_TOLERANCE);
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (IOException e) {
            throw new InvalidWebhookSignatureException("Unreadable payload.");
        }
        String eventId = event.path("id").asText();
        String type = event.path("type").asText();
        JsonNode session = event.path("data").path("object");

        GatewayEvent.Type normalized = switch (type) {
            case "checkout.session.completed" -> {
                String paymentStatus = session.path("payment_status").asText();
                // "unpaid" here means an async method (e.g. bank debit) is still processing.
                yield "paid".equals(paymentStatus) ? GatewayEvent.Type.PAYMENT_SUCCEEDED : GatewayEvent.Type.IGNORED;
            }
            case "checkout.session.async_payment_succeeded" -> GatewayEvent.Type.PAYMENT_SUCCEEDED;
            case "checkout.session.async_payment_failed" -> GatewayEvent.Type.PAYMENT_FAILED;
            case "checkout.session.expired" -> GatewayEvent.Type.SESSION_EXPIRED;
            default -> GatewayEvent.Type.IGNORED;
        };
        if (normalized == GatewayEvent.Type.IGNORED) {
            return GatewayEvent.ignored(eventId, type);
        }
        String currency = session.path("currency").asText("").toUpperCase(Locale.ROOT);
        BigDecimal amount = session.hasNonNull("amount_total") && !currency.isEmpty()
                ? MinorUnits.fromMinor(session.path("amount_total").asLong(), currency)
                : null;
        return new GatewayEvent(eventId, normalized, type, session.path("client_reference_id").asText(null),
                session.path("id").asText(null), amount, currency);
    }

    @Override
    public SessionStatus fetchSession(String providerSessionId) {
        try {
            JsonNode session = client.get()
                    .uri("/v1/checkout/sessions/{id}", providerSessionId)
                    .retrieve()
                    .body(JsonNode.class);
            if (session == null) {
                throw new PaymentGatewayException("Empty Stripe response.");
            }
            String status = session.path("status").asText();
            String paymentStatus = session.path("payment_status").asText();
            SessionState state = switch (status) {
                case "complete" -> "paid".equals(paymentStatus) ? SessionState.PAID : SessionState.OPEN;
                case "expired" -> SessionState.EXPIRED;
                default -> SessionState.OPEN;
            };
            String currency = session.path("currency").asText("").toUpperCase(Locale.ROOT);
            BigDecimal amount = session.hasNonNull("amount_total") && !currency.isEmpty()
                    ? MinorUnits.fromMinor(session.path("amount_total").asLong(), currency)
                    : null;
            return new SessionStatus(state, session.path("client_reference_id").asText(null), amount, currency);
        } catch (RestClientException e) {
            throw new PaymentGatewayException("Stripe session lookup failed", e);
        }
    }

    @Override
    public void expireSession(String providerSessionId) {
        try {
            client.post().uri("/v1/checkout/sessions/{id}/expire", providerSessionId).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.BAD_REQUEST) {
                throw new PaymentGatewayException("Stripe session expiry failed", e);
            }
            // 400: session is no longer open (already complete or expired) - nothing to do.
        } catch (RestClientException e) {
            throw new PaymentGatewayException("Stripe session expiry failed", e);
        }
    }
}
