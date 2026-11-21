package com.justfast.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Records each provider event we applied. The unique (provider, event_id) constraint makes webhook processing
 * idempotent even when the provider delivers the same event concurrently.
 */
@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedWebhookEvent() {
    }

    public ProcessedWebhookEvent(String provider, String eventId, String eventType, Instant processedAt) {
        this.provider = provider;
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }
}
