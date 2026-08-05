package charg.ing.stations.controller;


import charg.ing.stations.dto.StationDTO;
import charg.ing.stations.dto.StationPatchDTO;
import charg.ing.stations.dto.TransactionRequestDTO;
import charg.ing.stations.dto.TransactionResponseDTO;
import charg.ing.stations.audit.AuditEventPublisher;
import charg.ing.stations.dto.availability.AvailabilityResult;
import charg.ing.stations.dto.request.ServiceStatusRequest;
import charg.ing.stations.service.OcppRequestReplyService;
import charg.ing.stations.service.StationAvailabilityService;
import charg.ing.stations.service.StationService;
import charg.ing.stations.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;
    private final TransactionService transactionService;
    private final OcppRequestReplyService ocppRequestReplyService;
    private final ObjectMapper objectMapper;
    private final StationAvailabilityService availabilityService;
    private final AuditEventPublisher auditPublisher;

    /**
     * Получить все станции
     * Именно этот endpoint ищет state-updater-service
     */
    @GetMapping
    public ResponseEntity<List<StationDTO>> getAllStations() {
        List<StationDTO> stations = stationService.getAllStations();
        return ResponseEntity.ok(stations);
    }

    /**
     * Получить станцию по ID
     */
    @GetMapping("/{stationId}")
    public ResponseEntity<StationDTO> getStation(@PathVariable String stationId) {
        StationDTO station = stationService.getStationById(stationId);
        return ResponseEntity.ok(station);
    }

    /**
     * Получить станции с пагинацией
     */
    @GetMapping(params = {"page", "size"})
    public ResponseEntity<List<StationDTO>> getStationsPaginated(
            @RequestParam int page,
            @RequestParam int size) {
        List<StationDTO> stations = stationService.getStationsPaginated(page, size);
        return ResponseEntity.ok(stations);
    }



    @PatchMapping("/{chargeBoxId}")
    public ResponseEntity<StationPatchDTO> patchStation(
            @PathVariable String chargeBoxId,
            @RequestBody StationPatchDTO dto
    ) {
        return ResponseEntity.ok(
                stationService.patchStation(chargeBoxId, dto)
        );
    }

    /**
     * Перевод станции в/из эксплуатации оператором (IN_SERVICE / OUT_OF_SERVICE / MAINTENANCE).
     * Выключенная станция перестаёт быть доступной для брони и зарядки.
     */
    @PatchMapping("/{chargeBoxId}/service-status")
    public ResponseEntity<StationDTO> updateServiceStatus(
            @PathVariable String chargeBoxId,
            @RequestBody ServiceStatusRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        StationDTO updated = stationService.updateServiceStatus(chargeBoxId, request.getServiceStatus());
        Map<String, Object> payload = new HashMap<>();
        payload.put("serviceStatus", String.valueOf(request.getServiceStatus()));
        auditPublisher.publishChargeBox("SERVICE_STATUS_CHANGE", chargeBoxId,
                jwt != null ? jwt.getSubject() : null, "INFO",
                "Service status -> " + request.getServiceStatus(), payload);
        return ResponseEntity.ok(updated);
    }


    @PostMapping("/start-transaction")
    public Mono<ResponseEntity<TransactionResponseDTO>> startTransaction(
            @RequestBody TransactionRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        Map<String, Object> ocppRequest = new HashMap<>();
        ocppRequest.put("chargeBoxId", request.getChargeBoxId());
        ocppRequest.put("connectorId", request.getConnectorId());

        // Блокирующие пред-проверки (JPA + balance .block()) НЕЛЬЗЯ выполнять на event-loop потоке
        // WebFlux — выносим их на boundedElastic, иначе reactor бросает "block() ... not supported".
        return Mono.fromCallable(() -> runStartPrechecks(request, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(precheck -> {
                    if (precheck.error() != null) {
                        return Mono.just(precheck.error());
                    }
                    TransactionService.ChargingLimit limit = precheck.limit();

                    // ocppTag клиент больше не передаёт — берём его из каталога станции.
                    ocppRequest.put("ocppTag", precheck.ocppTag());

                    return ocppRequestReplyService.sendAndReceive(ocppRequest, 10, false)
                            .flatMap(responseMap -> {
                                // Преобразуем ответ в DTO
                                TransactionResponseDTO response = objectMapper.convertValue(responseMap, TransactionResponseDTO.class);

                                // РЕЗИЛЬЕНТНОСТЬ: SteVe/станция не подтвердили старт — в ответе нет
                                // transactionId (SteVe вернул 5xx, станция офлайн/отклонила и т.п.).
                                // НЕ пишем битую строку в БД (transaction_id NOT NULL → краш 500),
                                // а отдаём понятный ответ. Мобилка мапит X-Unavailable-Reason в текст.
                                if (response.getTransactionId() == null) {
                                    log.warn("Start not confirmed by station {} connector {} (no transactionId in OCPP response) — likely SteVe/OCPP failure",
                                            request.getChargeBoxId(), request.getConnectorId());
                                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                            .header("X-Unavailable-Reason", "START_FAILED")
                                            .<TransactionResponseDTO>build());
                                }

                                if (response.getUserId() == null) {
                                    response.setUserId(userId);
                                }
                                // Carry the pre-checked budget into the persistence step (avoids a second balance lookup).
                                response.setPricePerKwh(limit.pricePerKwh());
                                response.setMaxKwQuantity(limit.maxKwQuantity());

                                // Фиктивный Acknowledgment (не используется в HTTP-контексте)
                                org.springframework.kafka.support.Acknowledgment noopAck = () -> {};

                                // Сохранение оборачиваем: любой сбой персистентности → понятная 500,
                                // а не «сырое» исключение наружу.
                                try {
                                    transactionService.saveStartTransactionAndAck(response, noopAck);
                                } catch (Exception persistErr) {
                                    log.error("Failed to persist start transaction for station {} connector {}: {}",
                                            request.getChargeBoxId(), request.getConnectorId(), persistErr.toString(), persistErr);
                                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                            .header("X-Unavailable-Reason", "START_PERSIST_FAILED")
                                            .<TransactionResponseDTO>build());
                                }

                                Map<String, Object> auditPayload = new HashMap<>();
                                auditPayload.put("connectorId", request.getConnectorId());
                                auditPublisher.publishChargeBox("REMOTE_START", request.getChargeBoxId(), userId,
                                        "INFO", "Remote start requested by user", auditPayload);

                                return Mono.just(ResponseEntity.ok(response));
                            })
                            // Таймаут/сбой обмена с station-integration/SteVe — не роняем 500,
                            // отдаём 503 «станция недоступна» вместо сырого исключения.
                            .onErrorResume(err -> {
                                log.error("Start transaction OCPP exchange failed for station {} connector {}: {}",
                                        request.getChargeBoxId(), request.getConnectorId(), err.toString());
                                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                        .header("X-Unavailable-Reason", "STATION_UNREACHABLE")
                                        .<TransactionResponseDTO>build());
                            });
                });
    }

    /** Блокирующие пред-проверки старта (доступность станции + бюджет кошелька). Запускать вне event-loop. */
    private StartPrecheck runStartPrechecks(TransactionRequestDTO request, String userId) {
        // 1. Доступность: станция не выключена/offline, коннектор не Faulted/Unavailable и не занят другим.
        AvailabilityResult availability =
                availabilityService.checkChargeable(request.getChargeBoxId(), request.getConnectorId(), userId);
        if (!availability.available()) {
            HttpStatus status = switch (availability.reason()) {
                case STATION_NOT_FOUND, CONNECTOR_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case OUT_OF_SERVICE, OFFLINE -> HttpStatus.SERVICE_UNAVAILABLE;
                case RESERVED_BY_OTHER -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.CONFLICT; // NOT_OPERATIONAL и пр.
            };
            return StartPrecheck.error(ResponseEntity.status(status)
                    .header("X-Unavailable-Reason", availability.reason().name())
                    .build());
        }

        // 2. Бюджет: отклоняем, если баланс не покупает ни одного кВт·ч.
        TransactionService.ChargingLimit limit =
                transactionService.computeChargingLimit(userId, request.getChargeBoxId());
        if (limit.maxKwQuantity() != null && limit.maxKwQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return StartPrecheck.error(ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build());
        }

        // 3. ocppTag станции из каталога — подставляется в OCPP-запрос вместо поля из тела запроса.
        String ocppTag = stationService.getOcppTag(request.getChargeBoxId());

        return StartPrecheck.ok(limit, ocppTag);
    }

    /** Результат пред-проверок: либо готовый error-ответ, либо рассчитанный лимит и ocppTag для старта. */
    private record StartPrecheck(ResponseEntity<TransactionResponseDTO> error,
                                 TransactionService.ChargingLimit limit,
                                 String ocppTag) {
        static StartPrecheck error(ResponseEntity<TransactionResponseDTO> error) {
            return new StartPrecheck(error, null, null);
        }
        static StartPrecheck ok(TransactionService.ChargingLimit limit, String ocppTag) {
            return new StartPrecheck(null, limit, ocppTag);
        }
    }


    @PostMapping("/stop-transaction")
    public Mono<ResponseEntity<TransactionResponseDTO>> stopTransaction(
            @RequestBody TransactionRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();

        Map<String, Object> ocppRequest = new HashMap<>();
        ocppRequest.put("chargeBoxId", request.getChargeBoxId());
        ocppRequest.put("connectorId", request.getConnectorId());

        // ocppTag клиент больше не передаёт — резолвим его из каталога станции (блокирующий JPA-чтение
        // выносим на boundedElastic, чтобы не блокировать event-loop WebFlux).
        return Mono.fromCallable(() -> stationService.getOcppTag(request.getChargeBoxId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(ocppTag -> {
                    ocppRequest.put("ocppTag", ocppTag);

                    return ocppRequestReplyService.sendAndReceive(ocppRequest, 10, true)
                            .flatMap(responseMap -> {
                                TransactionResponseDTO response = objectMapper.convertValue(responseMap, TransactionResponseDTO.class);
                                if (response.getUserId() == null) {
                                    response.setUserId(userId);
                                }

                                org.springframework.kafka.support.Acknowledgment noopAck = () -> {};

                                transactionService.updateStopTransactionAndAck(response, noopAck);

                                Map<String, Object> auditPayload = new HashMap<>();
                                auditPayload.put("connectorId", request.getConnectorId());
                                auditPublisher.publishChargeBox("REMOTE_STOP", request.getChargeBoxId(), userId,
                                        "INFO", "Remote stop requested by user", auditPayload);

                                return Mono.just(ResponseEntity.ok(response));
                            });
                });
    }
}