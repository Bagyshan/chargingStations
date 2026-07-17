package de.rwth.idsg.steve.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Событие для централизованного журнала аудита (топик {@code audit.events} → audit-log-service).
 * {@code timestamp} держим строкой (ISO-8601), чтобы не зависеть от конфигурации JSON-сериализатора.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    private String eventId;
    private String timestamp;
    /** CHARGE_BOX | CONNECTOR | USER | BALANCE */
    private String eventType;
    private String action;
    private String userId;
    private String source;
    /** INFO | WARN | ERROR */
    private String severity;
    private String entityId;
    private String correlationId;
    private String ip;
    private String userAgent;
    private String message;
    private Map<String, Object> payload;
}
