package charg.ing.stations.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Событие для централизованного журнала аудита (топик {@code audit.events} → audit-log-service).
 * Общий контракт проекта; {@code eventId} — ключ Kafka-сообщения и дедуп в Elasticsearch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    private String eventId;
    private Instant timestamp;
    /** CHARGE_BOX | CONNECTOR | USER | BALANCE */
    private String eventType;
    private String action;
    /** Актор — кто инициировал событие. */
    private String userId;
    private String source;
    /** INFO | WARN | ERROR */
    private String severity;
    /** Id затронутой сущности. */
    private String entityId;
    private String correlationId;
    private String ip;
    private String userAgent;
    private String message;
    private Map<String, Object> payload;
}
