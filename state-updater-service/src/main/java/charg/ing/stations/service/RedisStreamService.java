package charg.ing.stations.service;

import charg.ing.stations.dto.StationEventDTO;
import charg.ing.stations.dto.StationStateDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisStreamService {

    private static final String STATION_EVENTS_STREAM = "station:events:stream";
    private static final String STATION_UPDATES_GROUP = "station-updaters";
    private static final String WEBSOCKET_CONSUMER = "websocket-service";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Публикует событие в Redis Stream
     */
    public Mono<String> publishEvent(StationEventDTO event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);

            Map<String, String> streamRecord = new HashMap<>();
            streamRecord.put("eventType", event.getEventType().name());
            streamRecord.put("stationId", event.getStationId());
            streamRecord.put("eventData", eventJson);
            streamRecord.put("timestamp", Instant.now().toString());

            log.debug("Publishing event to stream {}: {} - {}",
                    STATION_EVENTS_STREAM, event.getEventType(), event.getStationId());

            return redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord()
                            .in(STATION_EVENTS_STREAM)
                            .ofMap(streamRecord))
                    .map(RecordId::getValue)
                    .doOnSuccess(recordId ->
                            log.trace("Event published with recordId: {}", recordId)
                    )
                    .onErrorResume(e -> {
                        log.error("Failed to publish event to stream: {}", e.getMessage());
                        return Mono.empty();
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * Публикует событие обновления станции
     */
    public Mono<String> publishStationUpdate(StationStateDTO stationState, Integer oldVersion) {
        StationEventDTO event;

        if (oldVersion == null) {
            // Новая станция
            event = StationEventDTO.createStationCreatedEvent(stationState);
        } else {
            // Обновление существующей
            event = StationEventDTO.createStationUpdatedEvent(stationState, oldVersion);

            // Проверяем изменения в коннекторах
            // (Здесь можно добавить дополнительную логику сравнения старого и нового состояния)
        }

        return publishEvent(event);
    }

    /**
     * Публикует событие удаления станции
     */
    public Mono<String> publishStationDeletion(String stationId) {
        StationEventDTO event = StationEventDTO.createStationDeletedEvent(stationId);
        return publishEvent(event);
    }

    /**
     * Создает consumer group для Stream (если еще не существует)
     */
    public Mono<Boolean> createConsumerGroupIfNotExists(String consumerGroup, String consumerName) {
        return redisTemplate.opsForStream()
                .createGroup(STATION_EVENTS_STREAM, ReadOffset.from("0"), consumerGroup)
                .then(Mono.just(true))
                .onErrorResume(e -> {
                    if (e.getMessage().contains("BUSYGROUP")) {
                        log.debug("Consumer group {} already exists", consumerGroup);
                        return Mono.just(true);
                    }
                    log.error("Failed to create consumer group {}: {}", consumerGroup, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Читает события из Stream для указанного consumer
     */
    public Flux<Map<String, Object>> readEvents(String consumerGroup, String consumerName) {
        return createConsumerGroupIfNotExists(consumerGroup, consumerName)
                .thenMany(
                        redisTemplate.opsForStream()
                                .read(Consumer.from(consumerGroup, consumerName),
                                        StreamReadOptions.empty().count(10).block(Duration.ofSeconds(5)),
                                        StreamOffset.create(STATION_EVENTS_STREAM, ReadOffset.lastConsumed()))
                )
                .flatMap(record -> {
                    try {
                        Map<String, Object> result = new HashMap<>();
                        result.put("recordId", record.getId().getValue());
                        result.put("stream", record.getStream());

                        // Парсим данные из record
                        Map<String, String> recordData = new HashMap<>();
                        record.getValue().forEach((k, v) -> recordData.put((String) k, (String) v));

                        String eventDataJson = recordData.get("eventData");
                        if (eventDataJson != null) {
                            StationEventDTO event = objectMapper.readValue(eventDataJson, StationEventDTO.class);
                            result.put("event", event);
                        }

                        result.put("rawData", recordData);

                        return Mono.just(result);
                    } catch (Exception e) {
                        log.error("Failed to parse stream record: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error reading from stream: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Получает историю событий (последние N событий)
     */
    public Flux<StationEventDTO> getEventHistory(int count) {
        return redisTemplate.opsForStream()
                .reverseRange(STATION_EVENTS_STREAM, Range.closed("-", "+"), Limit.limit().count(count))
                .flatMap(record -> {
                    try {
                        String eventDataJson = (String) record.getValue().get("eventData");
                        if (eventDataJson != null) {
                            StationEventDTO event = objectMapper.readValue(eventDataJson, StationEventDTO.class);
                            return Mono.just(event);
                        }
                        return Mono.empty();
                    } catch (Exception e) {
                        log.error("Failed to parse historical record: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error reading event history: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Получает информацию о Stream
     */
    public Mono<Map<String, Object>> getStreamInfo() {
        return redisTemplate.opsForStream()
                .info(STATION_EVENTS_STREAM)
                .map(streamInfo -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("length", streamInfo.streamLength());
                    info.put("radixTreeKeys", streamInfo.radixTreeKeySize());
                    info.put("radixTreeNodes", streamInfo.radixTreeNodesSize());
                    info.put("groups", streamInfo.groupCount());
                    info.put("lastGeneratedId", streamInfo.lastGeneratedId());
                    info.put("firstEntry", streamInfo.firstEntryId());
                    info.put("lastEntry", streamInfo.lastEntryId());
                    return info;
                })
                .defaultIfEmpty(Map.of(
                        "error", "Stream not found",
                        "streamKey", STATION_EVENTS_STREAM
                ))
                .onErrorResume(e -> {
                    log.error("Error getting stream info: {}", e.getMessage());
                    return Mono.just(Map.of(
                            "error", e.getMessage(),
                            "streamKey", STATION_EVENTS_STREAM
                    ));
                });
    }

    /**
     * Очищает старые события (обрезает Stream до указанного размера)
     */
    public Mono<Long> trimStream(long maxLength) {
        return redisTemplate.opsForStream()
                .trim(STATION_EVENTS_STREAM, maxLength)
                .doOnSuccess(count ->
                        log.info("Trimmed stream {} to {} entries, removed {} entries",
                                STATION_EVENTS_STREAM, maxLength, count)
                )
                .onErrorResume(e -> {
                    log.error("Error trimming stream: {}", e.getMessage());
                    return Mono.just(0L);
                });
    }

    /**
     * Инициализирует Stream и consumer groups при старте
     */
    public Mono<Boolean> initializeStream() {
        log.info("Initializing Redis Stream: {}", STATION_EVENTS_STREAM);

        // Создаем основную consumer group
        return createConsumerGroupIfNotExists(STATION_UPDATES_GROUP, "state-updater-service")
                .flatMap(success -> {
                    if (success) {
                        log.info("Consumer group '{}' initialized", STATION_UPDATES_GROUP);
                    }
                    return Mono.just(success);
                })
                .onErrorResume(e -> {
                    log.error("Failed to initialize stream: {}", e.getMessage());
                    return Mono.just(false);
                });
    }
}