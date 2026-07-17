package charg.ing.stations.audit;

import charg.ing.stations.event.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Публикует события аудита в топик {@code audit.events}. Fire-and-forget: любые ошибки
 * логируются и НЕ пробрасываются, чтобы аудит никогда не ломал основной сценарий.
 *
 * <p>Использует отдельный {@code auditKafkaTemplate} (value = JSON-объект без type-заголовков),
 * а не строковый {@code kafkaTemplate} сервиса.
 */
@Slf4j
@Component
public class AuditEventPublisher {

    public static final String TOPIC = "audit.events";
    private static final String SOURCE = "user-service";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuditEventPublisher(@Qualifier("auditKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

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
            // key = eventId → повторная доставка одного события всегда в одну партицию.
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

    /** Маппит уже сформированный {@link UserEvent} в событие аудита типа USER. */
    public void publishUserEvent(UserEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        String action = event.getEventType().name();
        boolean failure = action.contains("FAILED");

        String actor = event.getKeycloakId() != null ? event.getKeycloakId()
                : (event.getUserId() != null ? String.valueOf(event.getUserId()) : event.getUserEmail());

        Map<String, Object> payload = new HashMap<>();
        putIfNotNull(payload, "userEmail", event.getUserEmail());
        putIfNotNull(payload, "userRole", event.getUserRole());
        putIfNotNull(payload, "localUserId", event.getUserId());
        putIfNotNull(payload, "keycloakId", event.getKeycloakId());
        if (event.getMetadata() != null) {
            event.getMetadata().forEach((k, v) -> {
                // Токены верификации/сброса в аудит не кладём.
                if (!"token".equalsIgnoreCase(k)) {
                    payload.put(k, v);
                }
            });
        }

        publish(AuditEvent.builder()
                .eventType("USER")
                .action(action)
                .userId(actor)
                .entityId(event.getUserEmail())
                .severity(failure ? "WARN" : "INFO")
                .ip(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .message(action + (event.getUserEmail() != null ? " (" + event.getUserEmail() + ")" : ""))
                .payload(payload)
                .build());
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
