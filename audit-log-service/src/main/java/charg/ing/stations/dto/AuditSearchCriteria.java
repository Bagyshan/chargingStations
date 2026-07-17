package charg.ing.stations.dto;

import java.time.Instant;

/**
 * Набор фильтров для поиска по журналу аудита. Все поля опциональны; незаполненные
 * не участвуют в запросе. {@code from}/{@code to} задают период по {@code timestamp}.
 * {@code payloadKey}/{@code payloadValue} — точечный фильтр по подполю flattened-payload.
 */
public record AuditSearchCriteria(
        String eventType,
        String action,
        String userId,
        String entityId,
        String source,
        String severity,
        String correlationId,
        Instant from,
        Instant to,
        String q,
        String payloadKey,
        String payloadValue,
        int page,
        int size
) {
}
