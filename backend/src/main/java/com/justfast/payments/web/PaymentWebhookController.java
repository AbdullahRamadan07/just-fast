package com.justfast.payments.web;

import com.justfast.payments.application.WebhookProcessor;
import com.justfast.payments.application.WebhookProcessor.Disposition;
import com.justfast.payments.gateway.GatewayEvent;
import com.justfast.payments.gateway.InvalidWebhookSignatureException;
import com.justfast.payments.gateway.PaymentGateway;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider → API webhooks. Not session-authenticated and exempt from CSRF; every request must carry a valid
 * provider signature over the raw body.
 */
@RestController
@RequestMapping("/api/payments/webhooks")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentGateway gateway;
    private final WebhookProcessor processor;

    public PaymentWebhookController(PaymentGateway gateway, WebhookProcessor processor) {
        this.gateway = gateway;
        this.processor = processor;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> receive(@PathVariable String provider,
                                                       @RequestBody String payload,
                                                       @RequestHeader HttpHeaders headers) {
        if (!gateway.provider().equals(provider)) {
            return ResponseEntity.notFound().build();
        }
        GatewayEvent event;
        try {
            event = gateway.parseWebhook(payload, headers);
        } catch (InvalidWebhookSignatureException e) {
            log.warn("Rejected {} webhook: {}", provider, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "invalid signature"));
        }
        Disposition disposition;
        try {
            disposition = processor.process(provider, event);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            disposition = Disposition.DUPLICATE;
        }
        log.info("{} webhook {} ({}) -> {}", provider, event.eventId(), event.rawType(), disposition);
        return ResponseEntity.ok(Map.of("result", disposition.name().toLowerCase()));
    }
}
