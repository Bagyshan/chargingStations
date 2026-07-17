package de.rwth.idsg.steve.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Публикует события аудита из station-steve в топик {@code audit.events} (fire-and-forget).
 * Переиспользует общий {@code KafkaTemplate<String,Object>} SteVe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventPublisher {

    public static final String TOPIC = "audit.events";
    private static final String SOURCE = "station-steve";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(AuditEvent event) {
        try {
            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }
            if (event.getTimestamp() == null) {
                event.setTimestamp(Instant.now().toString());
            }
            if (event.getSource() == null) {
                event.setSource(SOURCE);
            }
            if (event.getSeverity() == null) {
                event.setSeverity("INFO");
            }
            kafkaTemplate.send(TOPIC, event.getEventId(), event)
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.warn("Audit publish failed (action={}): {}", event.getAction(), ex.toString());
                        }
                    });
        } catch (Exception e) {
            log.warn("Audit publish threw (action={}): {}", event.getAction(), e.toString());
        }
    }

    /** Событие уровня станции (eventType = CHARGE_BOX). */
    public void publishChargeBox(String action, String chargeBoxId, String severity,
                                 String message, Map<String, Object> payload) {
        publish(AuditEvent.builder()
                .eventType("CHARGE_BOX")
                .action(action)
                .entityId(chargeBoxId)
                .severity(severity)
                .message(message)
                .payload(payload)
                .build());
    }
}
