package citrine.os.station.controller;

import citrine.os.station.dto.*;
import citrine.os.station.client.SteveClient;
import citrine.os.station.service.OcppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "SteVe Integration", description = "REST API для взаимодействия с SteVe")
public class OcppController {

    private final SteveClient steveClient;
    private final OcppService ocppService;

    @Operation(
            summary = "Запуск транзакции",
            description = "Удалённо запускает транзакцию через SteVe по идентификатору точки"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса")
    })
    @PostMapping("/remote/start")
    public Mono<ResponseEntity<OcppStartResponse>> remoteStart(
            @RequestBody RemoteStartRequest req
    ) {
        if (req.getConnectorId() == null) {
            return Mono.error(new IllegalArgumentException("connectorId is required"));
        }
        return ocppService.remoteStart(req);
    }

    @Operation(
            summary = "Остановка транзакции",
            description = "Удалённо останавливает транзакцию через SteVe по идентификатору точки"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса")
    })
    @PostMapping("/remote/stop")
    public Mono<ResponseEntity<OcppStopResponse>> remoteStop(
            @RequestBody RemoteStopRequest req
    ) {
        return ocppService.remoteStop(req);
    }

    @GetMapping("/connectors")
    public Mono<ResponseEntity<String>> getStatus() {
        return steveClient.getStatus();
    }
}

