package charg.ing.stations.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Публикует события аудита в топик {@code audit.events}. Fire-and-forget: ошибки логируются
 * и не пробрасываются, чтобы аудит не ломал основной сценарий. Переиспользует общий
 * {@code KafkaTemplate<String,Object>} сервиса (value = JSON).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventPublisher {

    public static final String TOPIC = "audit.events";
    private static final String SOURCE = "station-controll-service";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(AuditEvent event) {
        try {
            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }
            if (event.getTimestamp() == null) {
                event.setTimestamp(Instant.now());
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
    public void publishChargeBox(String action, String chargeBoxId, String userId,
                                 String severity, String message, Map<String, Object> payload) {
        publish(AuditEvent.builder()
                .eventType("CHARGE_BOX")
                .action(action)
                .userId(userId)
                .entityId(chargeBoxId)
                .severity(severity)
                .message(message)
                .payload(payload)
                .build());
    }

    /** Событие уровня коннектора (eventType = CONNECTOR); entityId = "chargeBoxId:connectorId". */
    public void publishConnector(String action, String chargeBoxId, Integer connectorId,
                                 String severity, String message, Map<String, Object> payload) {
        publish(AuditEvent.builder()
                .eventType("CONNECTOR")
                .action(action)
                .entityId(chargeBoxId + ":" + connectorId)
                .severity(severity)
                .message(message)
                .payload(payload)
                .build());
    }
}
