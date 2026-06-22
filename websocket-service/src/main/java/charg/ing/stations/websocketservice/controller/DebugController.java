package charg.ing.stations.websocketservice.controller;

import charg.ing.stations.websocketservice.service.RedisStreamConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final RedisStreamConsumerService redisStreamConsumerService;

    @GetMapping("/redis")
    public Mono<ResponseEntity<Map<String, Object>>> redisInfo() {
        return redisStreamConsumerService.getStreamInfo()
                .map(info -> {
                    log.info("Redis Stream Info: {}", info);
                    return ResponseEntity.ok(info);
                })
                .onErrorResume(e -> {
                    log.error("Error getting Redis info: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(500)
                            .body(Map.of("error", e.getMessage())));
                });
    }

    @GetMapping("/check-group")
    public Mono<ResponseEntity<Map<String, Object>>> checkConsumerGroup() {
        return redisStreamConsumerService.isOurConsumerGroupAvailable()
                .map(available -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("consumerGroup", "websocket-service");
                    response.put("available", available);
                    response.put("timestamp", System.currentTimeMillis());
                    return ResponseEntity.ok(response);
                });
    }

    @PostMapping("/create-group")
    public Mono<ResponseEntity<Map<String, Object>>> createConsumerGroup() {
        return Mono.fromCallable(() -> {
            // Прямой вызов Redis команды через template
            return ResponseEntity.ok(Map.of(
                    "message", "Use Redis CLI: XGROUP CREATE station:events:stream websocket-service $ MKSTREAM",
                    "command", "XGROUP CREATE station:events:stream websocket-service $ MKSTREAM"
            ));
        });
    }
}