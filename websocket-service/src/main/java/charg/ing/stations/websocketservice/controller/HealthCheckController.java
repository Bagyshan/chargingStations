package charg.ing.stations.websocketservice.controller;

import charg.ing.stations.websocketservice.service.RedisStreamConsumerService;
import charg.ing.stations.websocketservice.handler.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthCheckController {

    private final RedisStreamConsumerService redisStreamConsumerService;
    private final WebSocketSessionManager sessionManager;

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        log.info("Health check requested");

        return redisStreamConsumerService.getServiceStatus()
                .map(status -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "UP");
                    response.put("service", "websocket-service");
                    response.put("timestamp", Instant.now().toString());
                    response.put("activeConnections", sessionManager.getActiveSessionCount());
                    response.put("details", status);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Health check error: {}", e.getMessage());
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "DOWN");
                    response.put("service", "websocket-service");
                    response.put("timestamp", Instant.now().toString());
                    response.put("error", e.getMessage());
                    response.put("activeConnections", sessionManager.getActiveSessionCount());
                    return Mono.just(ResponseEntity.status(503).body(response));
                });
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "pong");
        response.put("timestamp", Instant.now().toString());
        response.put("service", "websocket-service");
        response.put("activeConnections", sessionManager.getActiveSessionCount());
        return ResponseEntity.ok(response);
    }
}