package citrine.os.station.controller;

import citrine.os.station.dto.*;
import citrine.os.station.client.SteveClient;
import citrine.os.station.service.OcppService;
import citrine.os.station.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "OCPP Remote Operations", description = "Удалённый запуск/остановка транзакций")
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class OcppController {

    private final SteveClient steveClient;
    private final OcppService ocppService;

//    @Operation(
//            summary = "Запуск транзакции",
//            description = "Удалённо запускает транзакцию через SteVe по идентификатору точки"
//    )
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Успешно"),
//            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса")
//    })
//    @PostMapping("/remote/start")
//    @Operation(summary = "Запуск транзакции", description = "Удалённо запускает транзакцию через SteVe")
//    public Mono<ResponseEntity<OcppResponse>> remoteStart(
//            @RequestBody RemoteStartRequest req
////            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
//    ) {
//
//
////        String token = extractToken(authHeader);
////        System.out.printf("AUTHORIZATION HEADER: {}%n", token);
////        Map<String, Object> claims = JwtUtil.parseToken(token);
////        System.out.printf("AUTHORIZATION DATA: {}%n", claims);
//
//
////        if (!JwtUtil.isEmailVerified(claims)) {
////            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email not verified");
////        }
//
////        if (req.getConnectorId() == null) {
////            return Mono.error(new IllegalArgumentException("connectorId is required"));
////        }
//
////        String userId = JwtUtil.getUserId(claims);
//        String userId = null;
//        return ocppService.remoteStart(req, userId);
//    }

//    @Operation(
//            summary = "Остановка транзакции",
//            description = "Удалённо останавливает транзакцию через SteVe по идентификатору точки"
//    )
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Успешно"),
//            @ApiResponse(responseCode = "400", description = "Неверные параметры запроса")
//    })
//    @PostMapping("/remote/stop")
//    @Operation(summary = "Остановка транзакции", description = "Удалённо останавливает транзакцию через SteVe")
//    public Mono<ResponseEntity<OcppResponse>> remoteStop(
//            @RequestBody RemoteStopRequest req
////            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
//    ) {
//
////        String token = extractToken(authHeader);
////        Map<String, Object> claims = JwtUtil.parseToken(token);
////
////        if (!JwtUtil.isEmailVerified(claims)) {
////            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email not verified");
////        }
//
////        String userId = JwtUtil.getUserId(claims);
//        String userId = null;
//        return ocppService.remoteStop(req, userId);
//    }

    @GetMapping("/connectors")
    public Mono<ResponseEntity<String>> getStatus() {
        return steveClient.getStatus();
    }


    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}

