package com.justfast.payments.gateway;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private static final String SECRET = "whsec_test";
    private static final String BODY = "{\"id\":\"evt_1\"}";
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    @Test
    void acceptsValidSignature() {
        String header = WebhookSignature.sign(SECRET, BODY, NOW);
        assertThatCode(() -> WebhookSignature.verify(SECRET, BODY, header, NOW, Duration.ofMinutes(5)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTamperedBody() {
        String header = WebhookSignature.sign(SECRET, BODY, NOW);
        assertThatThrownBy(() -> WebhookSignature.verify(SECRET, BODY.replace("1", "2"), header, NOW, Duration.ofMinutes(5)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsWrongSecret() {
        String header = WebhookSignature.sign("other", BODY, NOW);
        assertThatThrownBy(() -> WebhookSignature.verify(SECRET, BODY, header, NOW, Duration.ofMinutes(5)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsReplayedOldSignature() {
        String header = WebhookSignature.sign(SECRET, BODY, NOW.minus(Duration.ofMinutes(10)));
        assertThatThrownBy(() -> WebhookSignature.verify(SECRET, BODY, header, NOW, Duration.ofMinutes(5)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsMissingOrMalformedHeader() {
        assertThatThrownBy(() -> WebhookSignature.verify(SECRET, BODY, null, NOW, Duration.ofMinutes(5)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> WebhookSignature.verify(SECRET, BODY, "garbage", NOW, Duration.ofMinutes(5)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }
}
