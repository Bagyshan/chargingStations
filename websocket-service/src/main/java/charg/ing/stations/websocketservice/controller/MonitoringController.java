package charg.ing.stations.websocketservice.controller;


import charg.ing.stations.websocketservice.service.RedisStreamConsumerService;
import charg.ing.stations.websocketservice.handler.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final RedisStreamConsumerService redisStreamConsumerService;
    private final WebSocketSessionManager sessionManager;

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return redisStreamConsumerService.checkConnection()
                .map(redisConnected -> {
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "UP");
                    health.put("service", "websocket-service");
                    health.put("timestamp", Instant.now().toString());
                    health.put("redisConnected", redisConnected);
                    health.put("activeConnections", sessionManager.getActiveSessionCount());
                    return ResponseEntity.ok(health);
                });
    }

    @GetMapping("/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getStats() {
        return redisStreamConsumerService.getStreamInfo()
                .map(streamInfo -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("activeConnections", sessionManager.getActiveSessionCount());
                    stats.put("streamInfo", streamInfo);
                    stats.put("serverTime", Instant.now().toString());
                    return ResponseEntity.ok(stats);
                })
                .defaultIfEmpty(ResponseEntity.ok(Map.of(
                        "activeConnections", sessionManager.getActiveSessionCount(),
                        "streamInfo", Map.of("error", "Not available"),
                        "serverTime", Instant.now().toString()
                )));
    }

    @GetMapping("/clients")
    public ResponseEntity<Map<String, Object>> getActiveClients() {
        Map<String, Object> response = new HashMap<>();
        response.put("activeClients", sessionManager.getActiveClientsList());
        response.put("totalClients", sessionManager.getActiveSessionCount());
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/clients/{clientId}")
    public Mono<ResponseEntity<Map<String, String>>> disconnectClient(@PathVariable String clientId) {
        return Mono.fromRunnable(() -> {
                    WebSocketSession session = sessionManager.getSession(clientId);
                    if (session != null && session.isOpen()) {
                        session.close().subscribe(
                                null,
                                error -> log.warn("Error closing session for client {}: {}",
                                        clientId, error.getMessage())
                        );
                        log.info("Disconnect request sent for client: {}", clientId);
                    }
                })
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "message", "Disconnect request sent",
                        "clientId", clientId,
                        "timestamp", Instant.now().toString()
                ))))
                .onErrorResume(e ->
                        Mono.just(ResponseEntity.badRequest()
                                .body(Map.of(
                                        "error", e.getMessage(),
                                        "clientId", clientId
                                )))
                );
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupInactiveSessions(
            @RequestParam(defaultValue = "300") int maxInactivitySeconds) {

        sessionManager.cleanupInactiveSessions(Duration.ofSeconds(maxInactivitySeconds));

        return ResponseEntity.ok(Map.of(
                "message", "Cleanup completed",
                "maxInactivitySeconds", maxInactivitySeconds,
                "remainingClients", sessionManager.getActiveSessionCount(),
                "timestamp", Instant.now().toString()
        ));
    }
}









//
//import charg.ing.stations.websocketservice.handler.WebSocketSessionManager;
//import charg.ing.stations.websocketservice.service.RedisStreamConsumerService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.reactive.socket.WebSocketSession;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/monitoring")
//@RequiredArgsConstructor
//@Slf4j
//public class MonitoringController {
//
//    private final RedisStreamConsumerService redisStreamConsumerService;
//    private final WebSocketSessionManager sessionManager;
//
//    @GetMapping("/health")
//    public ResponseEntity<Map<String, Object>> health() {
//        Map<String, Object> health = new HashMap<>();
//        health.put("status", "UP");
//        health.put("service", "websocket-service");
//        health.put("timestamp", Instant.now().toString());
//        health.put("activeConnections", sessionManager.getActiveSessionCount());
//        return ResponseEntity.ok(health);
//    }
//
//    @GetMapping("/stats")
//    public Mono<ResponseEntity<Map<String, Object>>> getStats() {
//        return redisStreamConsumerService.getStreamInfo()
//                .map(streamInfo -> {
//                    Map<String, Object> stats = new HashMap<>();
//                    stats.put("activeConnections", sessionManager.getActiveSessionCount());
//                    stats.put("streamInfo", streamInfo);
//                    stats.put("serverTime", Instant.now().toString());
//                    return ResponseEntity.ok(stats);
//                })
//                .defaultIfEmpty(ResponseEntity.ok(Map.of(
//                        "activeConnections", sessionManager.getActiveSessionCount(),
//                        "streamInfo", Map.of("error", "Not available"),
//                        "serverTime", Instant.now().toString()
//                )));
//    }
//
//    @GetMapping("/clients")
//    public Flux<Map<String, Object>> getActiveClients() {
//        // Получаем информацию о клиентах из Redis
//        return redisStreamConsumerService.getActiveClients()
//                .collectList()
//                .flatMapMany(redisConsumers -> {
//                    // Создаем Map для быстрого поиска
//                    Map<String, Map<String, Object>> consumerMap = new HashMap<>();
//                    redisConsumers.forEach(consumer ->
//                            consumerMap.put((String) consumer.get("name"), consumer));
//
//                    // Возвращаем информацию о всех активных сессиях
//                    return Flux.fromIterable(sessionManager.getActiveClientsList())
//                            .map(clientInfo -> {
//                                Map<String, Object> client = new HashMap<>();
//                                client.put("clientId", clientInfo.get("clientId"));
//                                client.put("connectedSince", clientInfo.get("connectedSince"));
//                                client.put("lastActivity", clientInfo.get("lastActivity"));
//                                client.put("subscriptionCount", clientInfo.get("subscriptionCount"));
//
//                                // Добавляем информацию о Redis consumer
//                                String consumerName = "websocket-client-" + clientInfo.get("clientId");
//                                if (consumerMap.containsKey(consumerName)) {
//                                    client.put("redisConsumer", consumerMap.get(consumerName));
//                                } else {
//                                    client.put("redisConsumer", Map.of("status", "not found"));
//                                }
//
//                                client.put("sessionActive",
//                                        sessionManager.isSessionActive((String) clientInfo.get("clientId")));
//
//                                return client;
//                            });
//                })
//                .onErrorResume(e -> {
//                    log.error("Error getting clients info: {}", e.getMessage());
//                    return Flux.empty();
//                });
//    }
//
//    @DeleteMapping("/clients/{clientId}")
//    public Mono<ResponseEntity<Map<String, String>>> disconnectClient(@PathVariable String clientId) {
//        return Mono.fromRunnable(() -> {
//                    WebSocketSession session = sessionManager.getSession(clientId);
//                    if (session != null && session.isOpen()) {
//                        session.close().subscribe();
//                        log.info("Disconnected client by admin request: {}", clientId);
//                    }
//                })
//                .then(Mono.just(ResponseEntity.ok(Map.of(
//                        "message", "Disconnect request sent",
//                        "clientId", clientId,
//                        "timestamp", Instant.now().toString()
//                ))))
//                .onErrorResume(e ->
//                        Mono.just(ResponseEntity.badRequest()
//                                .body(Map.of("error", e.getMessage())))
//                );
//    }
//
//    @PostMapping("/cleanup")
//    public Mono<ResponseEntity<Map<String, Object>>> cleanupInactiveSessions(
//            @RequestParam(defaultValue = "300") int maxInactivitySeconds) {
//
//        return sessionManager.cleanupInactiveSessions(Duration.ofSeconds(maxInactivitySeconds))
//                .then(Mono.just(ResponseEntity.ok(Map.of(
//                        "message", "Cleanup completed",
//                        "maxInactivitySeconds", maxInactivitySeconds,
//                        "timestamp", Instant.now().toString()
//                ))));
//    }
//}