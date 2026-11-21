package com.justfast.payments.infrastructure;

import com.justfast.payments.domain.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, Long> {

    boolean existsByProviderAndEventId(String provider, String eventId);
}
