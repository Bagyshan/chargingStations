package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Входящее сообщение из Kafka-топика {@code audit.events}. Формат — plain-JSON без
 * type-headers (общий контракт проекта). Неизвестные поля игнорируем, чтобы аудит
 * никогда не падал из-за расширения контракта продюсерами.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditEventMessage {
    private String eventId;
    private Instant timestamp;
    private String eventType;
    private String action;
    private String userId;
    private String source;
    private String severity;
    private String entityId;
    private String correlationId;
    private String ip;
    private String userAgent;
    private String message;
    private Map<String, Object> payload;
}
