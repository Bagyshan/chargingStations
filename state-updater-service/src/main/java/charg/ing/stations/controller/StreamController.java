package charg.ing.stations.controller;

import charg.ing.stations.dto.StationEventDTO;
import charg.ing.stations.service.RedisStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    private final RedisStreamService redisStreamService;

    /**
     * Получить информацию о Stream
     * GET /api/stream/info
     */
    @GetMapping("/info")
    public Mono<ResponseEntity<Map<String, Object>>> getStreamInfo() {
        log.info("Getting Redis Stream info");

        return redisStreamService.getStreamInfo()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Получить историю событий
     * GET /api/stream/history?count=50
     */
    @GetMapping("/history")
    public Flux<StationEventDTO> getEventHistory(
            @RequestParam(defaultValue = "50") int count) {

        log.info("Getting last {} events from stream", count);

        return redisStreamService.getEventHistory(count)
                .doOnSubscribe(s -> log.debug("Starting history retrieval"))
                .doOnComplete(() -> log.debug("History retrieval completed"));
    }

    /**
     * Подписаться на события в реальном времени (SSE)
     * GET /api/stream/subscribe
     */
    @GetMapping(value = "/subscribe", produces = "text/event-stream")
    public Flux<Map<String, Object>> subscribeToEvents() {
        log.info("New SSE subscription to station events");

        String consumerGroup = "sse-consumers";
        String consumerName = "sse-" + Instant.now().toEpochMilli();

        return redisStreamService.readEvents(consumerGroup, consumerName)
                .doOnSubscribe(s ->
                        log.debug("SSE subscription started for consumer: {}", consumerName)
                )
                .doOnCancel(() ->
                        log.debug("SSE subscription cancelled for consumer: {}", consumerName)
                )
                .onErrorResume(e -> {
                    log.error("Error in SSE stream: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Обрезать Stream (удалить старые записи)
     * POST /api/stream/trim?maxLength=1000
     */
    @PostMapping("/trim")
    public Mono<ResponseEntity<Map<String, Object>>> trimStream(
            @RequestParam(defaultValue = "1000") long maxLength) {

        log.info("Trimming stream to max {} entries", maxLength);
        Map<String, Object> newStream = new HashMap<>();

        return redisStreamService.trimStream(maxLength)
                .map(count -> {
                    newStream.put("count", count);
                    newStream.put("maxLength", maxLength);
                    newStream.put("timestamp", Instant.now().toString());
                    return ResponseEntity.ok(newStream);
                })
//                .map(count -> ResponseEntity.ok(Map.of(
//                        "trimmedEntries", count,
//                        "newMaxLength", maxLength,
//                        "timestamp", Instant.now().toString()
//                )))
                .onErrorResume(e ->
                        Mono.just(ResponseEntity.badRequest()
                                .body(Map.of("error", e.getMessage())))
                );
    }

    /**
     * Тестовый endpoint для публикации тестового события
     * POST /api/stream/test-event
     */
    @PostMapping("/test-event")
    public Mono<ResponseEntity<Map<String, Object>>> publishTestEvent(
            @RequestParam String stationId,
            @RequestParam(defaultValue = "STATION_UPDATED") String eventType) {

        log.info("Publishing test event for station: {}, type: {}", stationId, eventType);

        // Создаем тестовое событие
        StationEventDTO.EventType type;
        try {
            type = StationEventDTO.EventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid event type: " + eventType)));
        }

        StationEventDTO testEvent = StationEventDTO.builder()
                .eventType(type)
                .stationId(stationId)
                .stationVersion(1)
                .metadata(Map.of("test", true, "source", "manual"))
                .build();

        return redisStreamService.publishEvent(testEvent)
                .map(recordId -> ResponseEntity.ok(Map.of(
                        "published", true,
                        "recordId", recordId,
                        "event", testEvent
                )))
                .defaultIfEmpty(ResponseEntity.status(500)
                        .body(Map.of("error", "Failed to publish event")));
    }
}