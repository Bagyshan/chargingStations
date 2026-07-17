package charg.ing.stations.controller;

import charg.ing.stations.dto.AuditSearchCriteria;
import charg.ing.stations.dto.PageResponse;
import charg.ing.stations.entity.AuditEvent;
import charg.ing.stations.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Чтение журнала аудита. Доступ ограничен ролями ADMIN/SPECIALIST (см. SecurityConfig).
 */
@Tag(name = "Audit", description = "Журнал аудита изменений сущностей (только ADMIN/SPECIALIST)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/audit/events")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService queryService;

    @Operation(summary = "Поиск событий аудита с фильтрами, периодом и пагинацией")
    @GetMapping
    public Mono<PageResponse<AuditEvent>> search(
            @Parameter(description = "Тип сущности: CHARGE_BOX | CONNECTOR | USER | BALANCE")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "Действие, напр. LOGIN, TOPUP, STATUS_CHANGE, ERROR")
            @RequestParam(required = false) String action,
            @Parameter(description = "Актор — кто инициировал событие")
            @RequestParam(required = false) String userId,
            @Parameter(description = "Id затронутой сущности (chargeBoxId/connectorId/targetUserId/walletId)")
            @RequestParam(required = false) String entityId,
            @Parameter(description = "Сервис-источник события")
            @RequestParam(required = false) String source,
            @Parameter(description = "Уровень: INFO | WARN | ERROR")
            @RequestParam(required = false) String severity,
            @Parameter(description = "Id корреляции (связать события одной операции)")
            @RequestParam(required = false) String correlationId,
            @Parameter(description = "Начало периода (ISO-8601, по timestamp)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Конец периода (ISO-8601, по timestamp)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Полнотекстовый поиск по message")
            @RequestParam(required = false) String q,
            @Parameter(description = "Ключ подполя payload для точечного фильтра (пара с payloadValue)")
            @RequestParam(required = false) String payloadKey,
            @Parameter(description = "Значение подполя payload")
            @RequestParam(required = false) String payloadValue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditSearchCriteria criteria = new AuditSearchCriteria(
                eventType, action, userId, entityId, source, severity, correlationId,
                from, to, q, payloadKey, payloadValue, page, size);
        return queryService.search(criteria);
    }

    @Operation(summary = "Получить одно событие аудита по eventId")
    @GetMapping("/{eventId}")
    public Mono<ResponseEntity<AuditEvent>> getById(@PathVariable String eventId) {
        return queryService.findById(eventId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
