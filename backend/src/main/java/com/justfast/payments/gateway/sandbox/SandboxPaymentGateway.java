package com.justfast.payments.gateway.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justfast.common.config.StoreProperties;
import com.justfast.payments.gateway.GatewayEvent;
import com.justfast.payments.gateway.InvalidWebhookSignatureException;
import com.justfast.payments.gateway.PaymentGateway;
import com.justfast.payments.gateway.PaymentProperties;
import com.justfast.payments.gateway.WebhookSignature;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Development-only stand-in for a hosted-checkout provider. It hosts a simple payment page, keeps sessions in
 * memory, and delivers HMAC-signed webhooks to the API exactly like a real provider would, so the complete
 * checkout → webhook → order-tracking flow can be exercised without merchant credentials.
 *
 * <p>Single-instance only (sessions are in memory). Use a real provider's sandbox (e.g. {@code stripe}) for shared
 * environments.
 */
@Component
@ConditionalOnProperty(prefix = "payments", name = "provider", havingValue = "sandbox", matchIfMissing = true)
public class SandboxPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "sandbox";
    public static final String SIGNATURE_HEADER = "Sandbox-Signature";

    private static final Logger log = LoggerFactory.getLogger(SandboxPaymentGateway.class);

    private final Map<String, SandboxSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionByAttempt = new ConcurrentHashMap<>();
    private final StoreProperties store;
    private final PaymentProperties.Sandbox config;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock;

    public SandboxPaymentGateway(StoreProperties store, PaymentProperties properties, ObjectMapper objectMapper,
                                 RestClient.Builder restClientBuilder, Clock clock) {
        this.store = store;
        this.config = properties.sandbox();
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.clock = clock;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutSessionRequest request) {
        String sessionId = sessionByAttempt.computeIfAbsent(request.paymentAttemptId(), attemptId -> {
            String id = "sbx_cs_" + UUID.randomUUID().toString().replace("-", "");
            sessions.put(id, new SandboxSession(id, request, SessionState.OPEN));
            return id;
        });
        return new CheckoutSession(sessionId, store.publicUrl("/api/sandbox-provider/checkout/" + sessionId));
    }

    @Override
    public GatewayEvent parseWebhook(String payload, HttpHeaders headers) {
        WebhookSignature.verify(config.webhookSecret(), payload, headers.getFirst(SIGNATURE_HEADER), clock.instant(),
                WebhookSignature.DEFAULT_TOLERANCE);
        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.path("type").asText();
            GatewayEvent.Type normalized = switch (type) {
                case "payment.succeeded" -> GatewayEvent.Type.PAYMENT_SUCCEEDED;
                case "payment.failed" -> GatewayEvent.Type.PAYMENT_FAILED;
                case "session.expired" -> GatewayEvent.Type.SESSION_EXPIRED;
                default -> GatewayEvent.Type.IGNORED;
            };
            return new GatewayEvent(event.path("id").asText(), normalized, type,
                    event.path("attemptId").asText(null), event.path("sessionId").asText(null),
                    new BigDecimal(event.path("amount").asText("0")), event.path("currency").asText(null));
        } catch (IOException | NumberFormatException e) {
            throw new InvalidWebhookSignatureException("Unreadable payload.");
        }
    }

    @Override
    public SessionStatus fetchSession(String providerSessionId) {
        SandboxSession session = sessions.get(providerSessionId);
        if (session == null) {
            // Lost on restart: report as expired so reconciliation can release stock.
            return new SessionStatus(SessionState.EXPIRED, null, null, null);
        }
        CheckoutSessionRequest r = session.request();
        return new SessionStatus(session.state(), r.paymentAttemptId(), r.amount(), r.currency());
    }

    @Override
    public void expireSession(String providerSessionId) {
        sessions.computeIfPresent(providerSessionId,
                (id, s) -> s.state() == SessionState.OPEN ? s.withState(SessionState.EXPIRED) : s);
    }

    // --- Hosted page support (used by SandboxProviderController) ---

    Optional<SandboxSession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId)).map(s ->
                s.state() == SessionState.OPEN && !clock.instant().isBefore(s.request().expiresAt())
                        ? s.withState(SessionState.EXPIRED)
                        : s);
    }

    /** Completes an open session with the given outcome and delivers the signed webhook. */
    Optional<SandboxSession> complete(String sessionId, SessionState outcome) {
        Optional<SandboxSession> current = find(sessionId);
        if (current.isEmpty() || current.get().state() != SessionState.OPEN) {
            return current;
        }
        SandboxSession updated = current.get().withState(outcome);
        sessions.put(sessionId, updated);
        deliverWebhook(updated, outcome == SessionState.PAID ? "payment.succeeded" : "payment.failed");
        return Optional.of(updated);
    }

    private void deliverWebhook(SandboxSession session, String type) {
        CheckoutSessionRequest r = session.request();
        Instant now = clock.instant();
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "id", "sbx_evt_" + UUID.randomUUID().toString().replace("-", ""),
                    "type", type,
                    "created", now.getEpochSecond(),
                    "sessionId", session.id(),
                    "attemptId", r.paymentAttemptId(),
                    "amount", r.amount().toPlainString(),
                    "currency", r.currency()));
            restClient.post()
                    .uri(config.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SIGNATURE_HEADER, WebhookSignature.sign(config.webhookSecret(), payload, now))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException | RestClientException e) {
            // Like a real provider outage: reconciliation will pick up the outcome via fetchSession().
            log.warn("Sandbox webhook delivery failed for session {}: {}", session.id(), e.getMessage());
        }
    }

    record SandboxSession(String id, CheckoutSessionRequest request, SessionState state) {

        SandboxSession withState(SessionState newState) {
            return new SandboxSession(id, request, newState);
        }
    }
}
