package charg.ing.stations.controller;


import charg.ing.stations.dto.StationDTO;
import charg.ing.stations.dto.StationPatchDTO;
import charg.ing.stations.dto.TransactionRequestDTO;
import charg.ing.stations.dto.TransactionResponseDTO;
import charg.ing.stations.dto.availability.AvailabilityResult;
import charg.ing.stations.dto.request.ServiceStatusRequest;
import charg.ing.stations.service.OcppRequestReplyService;
import charg.ing.stations.service.StationAvailabilityService;
import charg.ing.stations.service.StationService;
import charg.ing.stations.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;
    private final TransactionService transactionService;
    private final OcppRequestReplyService ocppRequestReplyService;
    private final ObjectMapper objectMapper;
    private final StationAvailabilityService availabilityService;

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
            @RequestBody ServiceStatusRequest request
    ) {
        return ResponseEntity.ok(
                stationService.updateServiceStatus(chargeBoxId, request.getServiceStatus())
        );
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
                                if (response.getUserId() == null) {
                                    response.setUserId(userId);
                                }
                                // Carry the pre-checked budget into the persistence step (avoids a second balance lookup).
                                response.setPricePerKwh(limit.pricePerKwh());
                                response.setMaxKwQuantity(limit.maxKwQuantity());

                                // Фиктивный Acknowledgment (не используется в HTTP-контексте)
                                org.springframework.kafka.support.Acknowledgment noopAck = () -> {};

                                // Вызываем существующий метод сохранения
                                transactionService.saveStartTransactionAndAck(response, noopAck);

                                return Mono.just(ResponseEntity.ok(response));
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

                                return Mono.just(ResponseEntity.ok(response));
                            });
                });
    }
}