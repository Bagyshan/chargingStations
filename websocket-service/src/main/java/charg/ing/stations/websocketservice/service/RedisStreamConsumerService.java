package charg.ing.stations.websocketservice.service;

import charg.ing.stations.websocketservice.dto.StationEventDTO;
import charg.ing.stations.websocketservice.dto.WebSocketMessageDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.channel.AbortedException;
import reactor.util.retry.Retry;
import com.fasterxml.jackson.databind.JsonNode;
import charg.ing.stations.websocketservice.dto.meter.MeterValueMessage;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisStreamConsumerService {

    private static final String STATION_EVENTS_STREAM = "station:events:stream";
    private static final String WEBSOCKET_CONSUMER_GROUP = "websocket-service";
    private static final String CONSUMER_PREFIX = "websocket-client-";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // Храним ID последнего сообщения для каждого клиента при подключении
    private final Map<String, String> clientStartMessageId = new ConcurrentHashMap<>();

    private final Map<String, ClientConnectionInfo> clientConnections = new ConcurrentHashMap<>();




    /**
     * Инициализирует consumer group при старте сервиса
     */
    public Mono<Boolean> initialize() {
        log.info("=== INITIALIZING REDIS STREAM CONSUMER ===");
        log.info("Stream: {}, Consumer Group: {}", STATION_EVENTS_STREAM, WEBSOCKET_CONSUMER_GROUP);

        if (isInitialized.get()) {
            log.info("Consumer already initialized");
            return Mono.just(true);
        }

        return checkStreamExists()
                .flatMap(streamExists -> {
                    if (streamExists) {
                        log.info("Stream '{}' exists", STATION_EVENTS_STREAM);
                        return createConsumerGroupForExistingStream();
                    } else {
                        log.info("Stream '{}' does not exist", STATION_EVENTS_STREAM);
                        return createStreamAndGroup();
                    }
                })
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("✅ Redis Stream consumer initialized successfully");
                        isInitialized.set(true);
                    } else {
                        log.error("❌ Failed to initialize Redis Stream consumer");
                    }
                })
                .doOnError(error ->
                        log.error("❌ Error during initialization: {}", error.getMessage(), error)
                );
    }

    /**
     * Читает все новые сообщения из Redis Stream и отправляет их клиенту.
     * Каждый клиент получает свою копию каждого сообщения.
     */
    public Flux<WebSocketMessageDTO> consumeEventsBroadcast(String clientId) {
        log.info("🚀 Starting broadcast event consumption for client: {}", clientId);

        return ensureInitialized()
                .flatMapMany(initialized -> {
                    if (!initialized) {
                        log.error("Cannot consume events - not initialized for client: {}", clientId);
                        return Flux.error(new IllegalStateException("Redis Stream consumer not initialized"));
                    }

                    return getCurrentStreamPosition()
                            .flatMapMany(startPosition -> {
                                log.info("Client {} will receive all messages after position: {}", clientId, startPosition);
                                AtomicReference<String> lastReadId = new AtomicReference<>(startPosition);

                                // Бесконечный цикл чтения с использованием repeat()
                                return Flux.defer(() -> {
                                    StreamOffset<String> offset = StreamOffset.create(
                                            STATION_EVENTS_STREAM,
                                            ReadOffset.from(lastReadId.get())
                                    );

                                    return redisTemplate.opsForStream()
                                            .<String, Object>read(offset) // Возвращает Flux<MapRecord>
                                            .doOnNext(record -> {
                                                String recordId = record.getId().getValue();
                                                lastReadId.set(recordId);
                                                log.trace("Read record {} for client {}", recordId, clientId);
                                            })
                                            .flatMap(record -> processStreamRecordForBroadcast(record, clientId))
                                            .onErrorResume(e -> {
                                                if (isConnectionClosedError(e)) {
                                                    log.debug("Connection closed, stopping broadcast for {}", clientId);
                                                    return Flux.empty();
                                                }
                                                log.warn("Error reading stream for client {}: {}", clientId, e.getMessage());
                                                return Flux.empty();
                                            })
                                            // Если сообщений нет, добавляем небольшую задержку перед следующей итерацией
                                            .switchIfEmpty(Mono.delay(Duration.ofSeconds(1)).thenMany(Flux.empty()));
                                }).repeat(); // Бесконечно повторяем после завершения потока
                            });
                })
                .doOnCancel(() -> log.info("⏹️ Broadcast consumption cancelled for client: {}", clientId))
                .doOnError(e -> log.error("❌ Broadcast error for client {}: {}", clientId, e.getMessage()));
    }


//    public Flux<WebSocketMessageDTO> consumeEventsBroadcast(String clientId) {
//        log.info("🚀 Starting broadcast event consumption for client: {}", clientId);
//
//        return ensureInitialized()
//                .flatMapMany(initialized -> {
//                    if (!initialized) {
//                        log.error("Cannot consume events - not initialized for client: {}", clientId);
//                        return Flux.empty();
//                    }
//
//                    // Запоминаем позицию, с которой начнём читать (последнее сообщение на момент подключения)
//                    return getCurrentStreamPosition()
//                            .flatMapMany(startPosition -> {
//                                log.info("Client {} will receive all messages after position: {}", clientId, startPosition);
//                                AtomicReference<String> lastReadId = new AtomicReference<>(startPosition);
//
//                                // Бесконечный цикл чтения с блокировкой
//                                return Flux.defer(() -> {
//                                    StreamOffset<String> offset = StreamOffset.create(
//                                            STATION_EVENTS_STREAM,
//                                            ReadOffset.from(lastReadId.get())
//                                    );
//                                    StreamReadOptions options = StreamReadOptions.empty()
//                                            .count(10)
//                                            .block(Duration.ofSeconds(30)); // ждём новые сообщения до 30 секунд
//
//                                    return redisTemplate.opsForStream()
//                                            .<String, Object>read(offset, options)
//                                            .flatMapMany(Flux::fromIterable)
//                                            .doOnNext(record -> {
//                                                String recordId = record.getId().getValue();
//                                                lastReadId.set(recordId);
//                                                log.trace("Read record {} for client {}", recordId, clientId);
//                                            })
//                                            .flatMap(record -> processStreamRecordForBroadcast(record, clientId))
//                                            .onErrorResume(e -> {
//                                                if (isConnectionClosedError(e)) {
//                                                    log.debug("Connection closed, stopping broadcast for {}", clientId);
//                                                    return Flux.empty();
//                                                }
//                                                log.warn("Error reading stream for client {}: {}", clientId, e.getMessage());
//                                                return Flux.empty();
//                                            });
//                                }).repeat(); // повторяем после завершения потока
//                            });
//                })
//                .doOnCancel(() -> log.info("⏹️ Broadcast consumption cancelled for client: {}", clientId))
//                .doOnError(e -> log.error("❌ Broadcast error for client {}: {}", clientId, e.getMessage()));
//    }


    private Mono<WebSocketMessageDTO> processStreamRecordForBroadcast(MapRecord<String, Object, Object> record,
                                                                      String clientId) {
        return Mono.fromCallable(() -> {
                    try {
                        Map<String, String> recordData = new HashMap<>();
                        record.getValue().forEach((k, v) ->
                                recordData.put(k.toString(), v != null ? v.toString() : null)
                        );

                        String eventDataJson = recordData.get("eventData");
                        if (eventDataJson == null) {
                            log.warn("Missing eventData in stream record {} for client {}",
                                    record.getId(), clientId);
                            throw new IllegalArgumentException("Missing eventData");
                        }

                        log.debug("Incoming eventDataJson: {}", eventDataJson);

                        JsonNode rootNode = objectMapper.readTree(eventDataJson);
                        String eventTypeStr = rootNode.get("eventType").asText();

                        StationEventDTO event;

                        if ("METER_VALUE".equals(eventTypeStr)) {
                            JsonNode eventDataNode = rootNode.get("eventData");
                            if (eventDataNode == null || eventDataNode.isNull()) {
                                log.warn("eventData is missing or null for record {}", record.getId());
                                throw new IllegalArgumentException("eventData missing");
                            }

                            MeterValueMessage meterValue = objectMapper.treeToValue(eventDataNode, MeterValueMessage.class);
                            String stationId = rootNode.get("stationId").asText();

                            event = StationEventDTO.builder()
                                    .eventType(StationEventDTO.EventType.METER_VALUE)
                                    .stationId(stationId)
                                    .timestamp(Instant.ofEpochMilli(meterValue.getTimestamp()))
                                    .meterValue(meterValue)
                                    .build();
                        } else {
                            event = objectMapper.readValue(eventDataJson, StationEventDTO.class);
                        }

                        WebSocketMessageDTO message = WebSocketMessageDTO.builder()
                                .type(WebSocketMessageDTO.MessageType.EVENT)
                                .event(event)
                                .timestamp(System.currentTimeMillis())
                                .build();

                        log.debug("✅ Processed event for client {}: {} - {} (recordId: {})",
                                clientId, event.getEventType(), event.getStationId(), record.getId());

                        return message;

                    } catch (JsonProcessingException e) {
                        log.error("❌ Failed to parse event data for client {} (record {}): {}",
                                clientId, record.getId(), e.getMessage());
                        throw new RuntimeException("Failed to parse event data", e);
                    } catch (Exception e) {
                        log.error("❌ Unexpected error processing record for client {}: {}",
                                clientId, record.getId(), e.getMessage(), e);
                        throw new RuntimeException("Failed to process record", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.debug("Skipping record due to error: {}", e.getMessage());
                    return Mono.empty();
                });
    }




    /**
     * Проверяет существование потока
     */
    private Mono<Boolean> checkStreamExists() {
        return redisTemplate.opsForStream()
                .info(STATION_EVENTS_STREAM)
                .map(streamInfo -> {
                    log.info("Stream info: length={}, groups={}",
                            streamInfo.streamLength(), streamInfo.groupCount());
                    return true;
                })
                .onErrorResume(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("no such key")) {
                        log.info("Stream '{}' does not exist", STATION_EVENTS_STREAM);
                        return Mono.just(false);
                    }
                    log.warn("Error checking stream existence: {}", e.getMessage());
                    return Mono.just(false);
                });
    }



    /**
     * Получает ID последнего сообщения в stream
     */
    private Mono<String> getLastStreamId() {
        return redisTemplate.opsForStream()
                .info(STATION_EVENTS_STREAM)
                .map(streamInfo -> {
                    String lastId = streamInfo.lastGeneratedId();
                    log.debug("Last stream ID: {}", lastId);
                    return lastId;
                })
                .defaultIfEmpty("$") // Если stream пустой, начинаем с "$" (новые сообщения)
                .onErrorResume(e -> {
            log.warn("Error getting last stream ID: {}", e.getMessage());
            return Mono.just("$");
        });
    }

    /**
     * Запоминает ID сообщения, с которого нужно начинать чтение для клиента
     */
    private Mono<Void> recordClientStartPosition(String clientId) {
        return getLastStreamId()
                .doOnSuccess(lastId -> {
                    // Сохраняем ID последнего сообщения на момент подключения
                    clientStartMessageId.put(clientId, lastId);
                    log.info("Client {} will start reading from message ID: {}", clientId, lastId);
                })
                .then();
    }




    /**
     * Создает consumer group для существующего потока
     */
    private Mono<Boolean> createConsumerGroupForExistingStream() {
        log.info("Creating consumer group '{}' for existing stream...", WEBSOCKET_CONSUMER_GROUP);

        return redisTemplate.opsForStream()
                .createGroup(STATION_EVENTS_STREAM, ReadOffset.latest(), WEBSOCKET_CONSUMER_GROUP)
                .then(Mono.just(true))
                .doOnSuccess(v -> log.info("✅ Consumer group created successfully"))
                .onErrorResume(e -> {
                    String errorMsg = e.getMessage();
                    log.info("Error creating consumer group: {}", errorMsg);

                    if (errorMsg != null && errorMsg.contains("BUSYGROUP")) {
                        log.info("Consumer group '{}' already exists, checking if it's valid...",
                                WEBSOCKET_CONSUMER_GROUP);
                        return checkConsumerGroupExists()
                                .map(exists -> {
                                    if (exists) {
                                        log.info("✅ Consumer group '{}' exists and is valid",
                                                WEBSOCKET_CONSUMER_GROUP);
                                        return true;
                                    } else {
                                        log.warn("Consumer group '{}' marked as BUSYGROUP but not found in stream info",
                                                WEBSOCKET_CONSUMER_GROUP);
                                        return false;
                                    }
                                });
                    }

                    log.error("❌ Failed to create consumer group: {}", errorMsg);
                    return Mono.just(false);
                });
    }

    /**
     * Проверяет существование consumer group через XINFO GROUPS
     */
    private Mono<Boolean> checkConsumerGroupExists() {
        log.debug("Checking for existence of consumer group '{}'", WEBSOCKET_CONSUMER_GROUP);

        return redisTemplate.opsForStream()
                .groups(STATION_EVENTS_STREAM)
                .collectList()
                .map(groupsList -> {
                    boolean groupExists = groupsList.stream()
                            .anyMatch(group -> group.groupName().equals(WEBSOCKET_CONSUMER_GROUP));

                    if (groupExists) {
                        log.info("Consumer group '{}' found in stream", WEBSOCKET_CONSUMER_GROUP);
                        groupsList.forEach(group ->
                                log.debug("Group: {}, consumers: {}, pending: {}",
                                        group.groupName(), group.consumerCount(), group.pendingCount())
                        );
                    } else {
                        log.warn("Consumer group '{}' not found in stream", WEBSOCKET_CONSUMER_GROUP);
                    }

                    return groupExists;
                })
                .onErrorResume(e -> {
                    log.error("Error checking consumer group existence: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Создает поток и consumer group
     */
    private Mono<Boolean> createStreamAndGroup() {
        log.info("Creating stream '{}' and consumer group '{}'...",
                STATION_EVENTS_STREAM, WEBSOCKET_CONSUMER_GROUP);

        Map<String, String> initialData = new HashMap<>();
        initialData.put("init", "true");
        initialData.put("timestamp", String.valueOf(System.currentTimeMillis()));
        initialData.put("service", "websocket-service");
        initialData.put("action", "stream-created");

        return redisTemplate.opsForStream()
                .add(Record.of(initialData).withStreamKey(STATION_EVENTS_STREAM))
                .doOnSuccess(recordId ->
                        log.info("✅ Stream created with initial message, recordId: {}", recordId)
                )
                .doOnError(error -> {
                    log.error("❌ Failed to create stream: {}", error.getMessage());
                    if (error.getMessage() != null && error.getMessage().contains("BUSYGROUP")) {
                        log.info("Stream might already exist, trying to create consumer group...");
                    }
                })
                .then(createConsumerGroupForExistingStream())
                .onErrorResume(e -> {
                    log.error("Error in createStreamAndGroup: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Безопасное создание consumer group
     */
    private Mono<Boolean> safeCreateConsumerGroup() {
        log.info("Attempting to safely create consumer group '{}'", WEBSOCKET_CONSUMER_GROUP);

        // Сначала проверяем, существует ли группа
        return checkConsumerGroupExists()
                .flatMap(groupExists -> {
                    if (groupExists) {
                        log.info("Consumer group '{}' already exists, skipping creation", WEBSOCKET_CONSUMER_GROUP);
                        return Mono.just(true);
                    }

                    // Группы нет, пытаемся создать
                    log.info("Consumer group '{}' does not exist, creating...", WEBSOCKET_CONSUMER_GROUP);
                    return redisTemplate.opsForStream()
                            .createGroup(STATION_EVENTS_STREAM, ReadOffset.latest(), WEBSOCKET_CONSUMER_GROUP)
                            .then(Mono.just(true))
                            .onErrorResume(e -> {
                                String errorMsg = e.getMessage();
                                log.error("Failed to create consumer group: {}", errorMsg);
                                return Mono.just(false);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error in safeCreateConsumerGroup: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Проверяет и гарантирует инициализацию
     */
    public Mono<Boolean> ensureInitialized() {
        if (isInitialized.get()) {
            return Mono.just(true);
        }

        log.warn("Consumer not initialized, attempting to initialize now...");
        // Просто проверяем, что группа существует
        return checkConsumerGroupExists()
                .doOnSuccess(groupExists -> {
                    if (groupExists) {
                        log.info("✅ Consumer group '{}' exists", WEBSOCKET_CONSUMER_GROUP);
                        isInitialized.set(true);
                    } else {
                        log.warn("Consumer group '{}' does not exist", WEBSOCKET_CONSUMER_GROUP);
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ Error ensuring initialization: {}", e.getMessage());
                    return Mono.just(false);
                });
    }





    /**
     * Читает события из Stream для конкретного клиента (только новые сообщения)
     */
    public Flux<WebSocketMessageDTO> consumeEvents(String clientId) {
        String consumerName = CONSUMER_PREFIX + clientId;

        log.info("🚀 Starting event consumption for client: {} (consumer: {})",
                clientId, consumerName);

        return ensureInitialized()
                .then(recordClientStartPosition(clientId))
                .thenMany(Flux.defer(() -> {
                    String startMessageId = clientStartMessageId.getOrDefault(clientId, "$");

                    return Flux.usingWhen(
                            // Ресурс: создаем consumer
                            Mono.fromCallable(() -> {
                                Consumer consumer = Consumer.from(WEBSOCKET_CONSUMER_GROUP, consumerName);
                                log.debug("Creating consumer {} in group {}", consumerName, WEBSOCKET_CONSUMER_GROUP);
                                return consumer;
                            }),
                            // Использование ресурса: читаем сообщения
                            consumer -> {
                                // Читаем ТОЛЬКО новые сообщения (с момента подключения)
                                StreamOffset<String> streamOffset = StreamOffset.create(STATION_EVENTS_STREAM,
                                        ReadOffset.from(startMessageId));

                                StreamReadOptions options = StreamReadOptions.empty()
                                        .count(10)
                                        .block(Duration.ofSeconds(30));

                                log.debug("Reading from stream with options: block={}s, count={}, startId={}",
                                        30, 10, startMessageId);

                                // Создаем бесконечный поток чтения ТОЛЬКО новых сообщений
                                return Flux.<List<MapRecord<String, Object, Object>>>generate(sink -> {
                                            sink.next(null); // Генерируем сигнал для следующего чтения
                                        })
                                        .concatMap(ignore ->
                                                redisTemplate.opsForStream()
                                                        .read(consumer, options, streamOffset)
                                                        .collectList()
                                        )
                                        .doOnNext(records -> {
                                            if (!records.isEmpty()) {
                                                log.debug("Received {} new records for client: {}", records.size(), clientId);
                                                // Обновляем startMessageId для следующего чтения
                                                String lastRecordId = records.get(records.size() - 1).getId().getValue();
                                                clientStartMessageId.put(clientId, lastRecordId);
                                            }
                                        })
                                        .flatMapIterable(records -> records)
                                        .flatMap(record -> processStreamRecord(record, clientId))
                                        // Игнорируем сообщения с ID меньше или равным начальному
                                        .filter(message -> true) // Все сообщения уже новые (читаем с startMessageId)
                                        .doOnSubscribe(s ->
                                                log.info("📡 Subscribed to Redis Stream for client: {} (starting from: {})",
                                                        clientId, startMessageId)
                                        )
                                        .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                                                .maxBackoff(Duration.ofSeconds(10))
                                                .doBeforeRetry(retrySignal -> {
                                                    if (retrySignal.failure() != null) {
                                                        log.warn("🔄 Retrying Redis Stream for client {} after error: {}",
                                                                clientId, retrySignal.failure().getMessage());
                                                    }
                                                })
                                        )
                                        .doOnError(e -> {
                                            if (!isConnectionClosedError(e)) {
                                                log.error("❌ Error in Redis Stream consumption for client {}: {}",
                                                        clientId, e.getMessage(), e);
                                            }
                                        })
                                        .doOnCancel(() ->
                                                log.info("⏹️ Redis Stream consumption cancelled for client: {}", clientId)
                                        )
                                        .doOnComplete(() ->
                                                log.info("✅ Redis Stream consumption completed for client: {}", clientId)
                                        );
                            },
                            // Очистка ресурса
                            consumer -> Mono.fromRunnable(() -> {
                                log.debug("Cleaning up consumer for client: {}", clientId);
                                clientStartMessageId.remove(clientId);
                            })
                    );
                }));
    }

    /**
     * Альтернативная, более простая версия - читать только сообщения после определенного ID
     */
    /**
     * Простая версия - используем blocking операцию в цикле
     */
    public Flux<WebSocketMessageDTO> consumeEventsSimple(String clientId) {
        String consumerName = CONSUMER_PREFIX + clientId;

        log.info("🚀 Starting event consumption for client: {} (consumer: {})",
                clientId, consumerName);

        return ensureInitialized()
                .flatMapMany(initialized -> {
                    if (!initialized) {
                        log.error("Cannot consume events - consumer group not initialized for client: {}", clientId);
                        return Flux.empty();
                    }

                    log.debug("Creating consumer {} in group {}", consumerName, WEBSOCKET_CONSUMER_GROUP);

                    Consumer consumer = Consumer.from(WEBSOCKET_CONSUMER_GROUP, consumerName);

                    // Используем '$' для чтения ТОЛЬКО новых сообщений (после текущего момента)
                    StreamOffset<String> streamOffset = StreamOffset.create(STATION_EVENTS_STREAM,
                            ReadOffset.from("$"));

                    StreamReadOptions options = StreamReadOptions.empty()
                            .count(10)
                            .block(Duration.ofSeconds(30));

                    log.debug("Reading NEW messages for client: {} with block={}s, count={}",
                            clientId, 30, 10);

                    // Простой подход: используем generate с бесконечным циклом
                    return Flux.generate(() -> null, (state, sink) -> {
                                // Этот метод будет вызываться для генерации каждого элемента
                                // Но реальное чтение происходит в flatMap
                                sink.next(null);
                                return state;
                            })
                            .concatMap(ignore ->
                                    redisTemplate.opsForStream()
                                            .read(consumer, options, streamOffset)
                                            .doOnSubscribe(s ->
                                                    log.trace("Reading batch from Redis Stream for client: {}", clientId)
                                            )
                                            .doOnNext(record ->
                                                    log.debug("📥 Received NEW record for client {}: {}", clientId, record.getId())
                                            )
                                            .flatMap(record -> processStreamRecord(record, clientId))
                            )
                            .doOnSubscribe(s ->
                                    log.info("📡 Subscribed to NEW Redis Stream messages for client: {}", clientId)
                            )
                            .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                                    .maxBackoff(Duration.ofSeconds(10))
                            )
                            .doOnCancel(() ->
                                    log.info("⏹️ Redis Stream consumption cancelled for client: {}", clientId)
                            );
                });
    }

    /**
     * ВАЖНО: Эта версия использует ReadOffset.latest() для чтения ТОЛЬКО новых сообщений
     */
    /**
     * Читает события из Stream для конкретного клиента (только новые сообщения после подключения)
     */
    /**
     * Оптимизированный метод для чтения ТОЛЬКО новых сообщений после подключения
     * Использует подход "запоминания позиции подключения"
     */
    public Flux<WebSocketMessageDTO> consumeEventsLatestOnly(String clientId) {
        String consumerName = CONSUMER_PREFIX + clientId;

        log.info("🚀 Starting event consumption for client: {} (consumer: {})",
                clientId, consumerName);

        // Регистрируем подключение клиента
        ClientConnectionInfo connectionInfo = new ClientConnectionInfo(
                consumerName,
                java.time.Instant.now().toString(),
                true
        );
        clientConnections.put(clientId, connectionInfo);

        return ensureInitialized()
                .flatMapMany(initialized -> {
                    if (!initialized) {
                        log.error("Cannot consume events - consumer group not initialized for client: {}", clientId);
                        return Flux.empty();
                    }

                    log.debug("Creating consumer {} in group {}", consumerName, WEBSOCKET_CONSUMER_GROUP);
                    Consumer consumer = Consumer.from(WEBSOCKET_CONSUMER_GROUP, consumerName);

                    return getCurrentStreamPosition()
                            .flatMapMany(startPosition -> {
                                log.info("Client {} will receive messages AFTER position: {}", clientId, startPosition);

                                return Flux.defer(() -> {
                                            AtomicBoolean isActive = new AtomicBoolean(true);

                                            return Flux.<WebSocketMessageDTO>create(sink -> {
                                                // Создаем final ссылки для использования в лямбде
                                                final Consumer finalConsumer = consumer;
                                                final String finalStartPosition = startPosition;

                                                // Функция для чтения сообщений (теперь как лямбда)
                                                Runnable readMessagesTask = new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (!isActive.get() || sink.isCancelled()) {
                                                            return;
                                                        }

                                                        readMessagesFromPosition(finalConsumer, finalStartPosition, clientId, isActive)
                                                                .doOnNext(sink::next)
                                                                .doOnError(error -> {
                                                                    if (!isConnectionClosedError(error)) {
                                                                        log.warn("Error reading from Redis Stream for client {}: {}",
                                                                                clientId, error.getMessage());
                                                                    }
                                                                })
                                                                .doOnComplete(() -> {
                                                                    // Планируем следующее чтение
                                                                    if (isActive.get() && !sink.isCancelled()) {
                                                                        Schedulers.parallel().schedule(this);
                                                                    }
                                                                })
                                                                .subscribe();
                                                    }
                                                };

                                                // Начинаем чтение
                                                log.info("📡 Subscribed to NEW Redis Stream messages for client: {} (after position: {})",
                                                        clientId, startPosition);
                                                readMessagesTask.run();

                                                // Обработка отмены
                                                sink.onCancel(() -> {
                                                    log.info("⏹️ Redis Stream consumption cancelled for client: {}", clientId);
                                                    isActive.set(false);
                                                    cleanupClientConnection(clientId);
                                                });

                                                sink.onDispose(() -> {
                                                    log.debug("Redis Stream consumption disposed for client: {}", clientId);
                                                    isActive.set(false);
                                                    cleanupClientConnection(clientId);
                                                });
                                            });
                                        })
                                        .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                                                .maxBackoff(Duration.ofSeconds(10))
                                                .doBeforeRetry(retrySignal -> {
                                                    if (retrySignal.failure() != null) {
                                                        log.warn("🔄 Retrying Redis Stream for client {} after error: {}",
                                                                clientId, retrySignal.failure().getMessage());
                                                    }
                                                })
                                        );
                            });
                })
                .doOnError(error -> {
                    if (!isConnectionClosedError(error)) {
                        log.error("❌ Error in Redis Stream consumption for client {}: {}",
                                clientId, error.getMessage());
                    }
                    cleanupClientConnection(clientId);
                });
    }


    /**
     * Получает текущую позицию в стриме (ID последнего сообщения)
     * Это сообщение будет последним перед подключением клиента
     */
    private Mono<String> getCurrentStreamPosition() {
        return redisTemplate.opsForStream()
                .info(STATION_EVENTS_STREAM)
                .map(streamInfo -> {
                    String lastId = streamInfo.lastGeneratedId();
                    log.debug("Current stream position: {}", lastId);

                    // Если стрим пустой, возвращаем специальный маркер
                    if ("0-0".equals(lastId) || lastId == null) {
                        return "0-0";
                    }

                    return lastId;
                })
                .defaultIfEmpty("0-0")
                .onErrorResume(e -> {
                    log.warn("Error getting current stream position: {}", e.getMessage());
                    return Mono.just("0-0");
                });
    }


    /**
     * Читает сообщения начиная с указанной позиции (исправленная версия)
     */
    private Flux<WebSocketMessageDTO> readMessagesFromPosition(Consumer consumer, String startPosition,
                                                               String clientId, AtomicBoolean isActive) {
        // Используем XREADGROUP с явной фильтрацией по ID
        StreamOffset<String> streamOffset = StreamOffset.create(
                STATION_EVENTS_STREAM,
                ReadOffset.from(">")  // Только НОВЫЕ сообщения
        );

        StreamReadOptions options = StreamReadOptions.empty()
                .count(10)
                .block(Duration.ofSeconds(30));

        return redisTemplate.opsForStream()
                .read(consumer, options, streamOffset)
                .takeWhile(record -> isActive.get())
                .filter(record -> {
                    // Фильтруем сообщения: пропускаем те, что были до нашего подключения
                    String recordId = record.getId().getValue();

                    // Если startPosition = "0-0", принимаем все сообщения
                    if ("0-0".equals(startPosition)) {
                        return true;
                    }

                    // Сравниваем ID сообщения с позицией подключения
                    return isMessageIdAfter(recordId, startPosition);
                })
                .flatMap(record -> processStreamRecord(record, clientId))
                .doOnNext(message ->
                        log.trace("📥 Processed NEW record for client {}: {}", clientId, message.getEvent().getEventType())
                );
    }

    /**
     * Сравнивает ID сообщений Redis Stream
     * Формат: "timestamp-sequence" (например, "1708771200000-0")
     * Возвращает true, если recordId идет ПОСЛЕ startPosition
     */
    private boolean isMessageIdAfter(String recordId, String startPosition) {
        try {
            // Разбираем ID на timestamp и sequence
            String[] recordParts = recordId.split("-");
            String[] startParts = startPosition.split("-");

            if (recordParts.length < 2 || startParts.length < 2) {
                log.warn("Invalid stream ID format: recordId={}, startPosition={}", recordId, startPosition);
                return true; // При ошибке принимаем сообщение
            }

            long recordTimestamp = Long.parseLong(recordParts[0]);
            long recordSequence = Long.parseLong(recordParts[1]);

            long startTimestamp = Long.parseLong(startParts[0]);
            long startSequence = Long.parseLong(startParts[1]);

            // Сравниваем timestamp и sequence
            if (recordTimestamp > startTimestamp) {
                return true;
            } else if (recordTimestamp == startTimestamp) {
                return recordSequence > startSequence;
            } else {
                return false;
            }

        } catch (Exception e) {
            log.warn("Error comparing stream IDs: recordId={}, startPosition={}, error={}",
                    recordId, startPosition, e.getMessage());
            return true; // При ошибке принимаем сообщение
        }
    }


    private boolean isConnectionClosedError(Throwable throwable) {
        return throwable instanceof AbortedException ||
                throwable instanceof IOException ||
                (throwable.getMessage() != null &&
                        (throwable.getMessage().contains("Connection has been closed") ||
                                throwable.getMessage().contains("connection reset") ||
                                throwable.getMessage().contains("Broken pipe")));
    }


    private Mono<WebSocketMessageDTO> processStreamRecord(MapRecord<String, Object, Object> record,
                                                          String clientId) {
        return Mono.fromCallable(() -> {
                    try {
                        Map<String, String> recordData = new HashMap<>();
                        record.getValue().forEach((k, v) ->
                                recordData.put(k.toString(), v != null ? v.toString() : null)
                        );

                        String eventDataJson = recordData.get("eventData");
                        if (eventDataJson == null) {
                            log.warn("Missing eventData in stream record {} for client {}",
                                    record.getId(), clientId);
                            acknowledgeMessageAsync(clientId, record.getId().getValue());
                            throw new IllegalArgumentException("Missing eventData");
                        }

                        log.debug("Incoming eventDataJson: {}", eventDataJson);

                        JsonNode rootNode = objectMapper.readTree(eventDataJson);
                        String eventTypeStr = rootNode.get("eventType").asText();

                        StationEventDTO event;

                        if ("METER_VALUE".equals(eventTypeStr)) {
                            // Извлекаем поле eventData (внутреннее сообщение)
                            JsonNode eventDataNode = rootNode.get("eventData");
                            if (eventDataNode == null || eventDataNode.isNull()) {
                                log.warn("eventData is missing or null for record {}", record.getId());
                                throw new IllegalArgumentException("eventData missing");
                            }

                            // Десериализуем eventDataNode в MeterValueMessage
                            MeterValueMessage meterValue = objectMapper.treeToValue(eventDataNode, MeterValueMessage.class);

                            // Для отладки – проверим, что поля заполнены
                            if (meterValue.getChargeBoxId() == null) {
                                log.warn("MeterValueMessage fields are null after deserialization for record {}", record.getId());
                            }

                            // Берём stationId из верхнего уровня (или из meterValue – как вам удобнее)
                            String stationId = rootNode.get("stationId").asText();

                            event = StationEventDTO.builder()
                                    .eventType(StationEventDTO.EventType.METER_VALUE)
                                    .stationId(stationId)
                                    .timestamp(Instant.ofEpochMilli(meterValue.getTimestamp())) // из внутреннего timestamp
                                    .meterValue(meterValue)
                                    .build();
                        } else {
                            // Обычное событие
                            event = objectMapper.readValue(eventDataJson, StationEventDTO.class);
                        }

                        WebSocketMessageDTO message = WebSocketMessageDTO.builder()
                                .type(WebSocketMessageDTO.MessageType.EVENT)
                                .event(event)
                                .timestamp(System.currentTimeMillis())
                                .build();

                        log.debug("✅ Processed event for client {}: {} - {} (recordId: {})",
                                clientId, event.getEventType(), event.getStationId(), record.getId());

                        acknowledgeMessageAsync(clientId, record.getId().getValue());

                        return message;

                    } catch (JsonProcessingException e) {
                        log.error("❌ Failed to parse event data for client {} (record {}): {}",
                                clientId, record.getId(), e.getMessage());
                        acknowledgeMessageAsync(clientId, record.getId().getValue());
                        throw new RuntimeException("Failed to parse event data", e);
                    } catch (Exception e) {
                        log.error("❌ Unexpected error processing record for client {}: {}",
                                clientId, record.getId(), e.getMessage(), e);
                        acknowledgeMessageAsync(clientId, record.getId().getValue());
                        throw new RuntimeException("Failed to process record", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.debug("Skipping record due to error: {}", e.getMessage());
                    return Mono.empty();
                });
    }


    /**
     * Асинхронно подтверждает обработку сообщения (без блокировки основного потока)
     */
    private void acknowledgeMessageAsync(String clientId, String recordId) {
        acknowledgeMessage(clientId, recordId)
                .doOnError(e ->
                        log.warn("Failed to ack message {} for client {}: {}",
                                recordId, clientId, e.getMessage())
                )
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * Очищает ресурсы клиента при отключении
     */
    private void cleanupClientConnection(String clientId) {
        ClientConnectionInfo info = clientConnections.remove(clientId);
        if (info != null) {
            info.setActive(false);
            log.debug("Cleaned up connection for client: {}", clientId);
        }
    }

    /**
     * Получает статус всех подключений
     */
    public Mono<Map<String, Object>> getConnectionsStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("totalConnections", clientConnections.size());
        status.put("connections", new HashMap<>(clientConnections));
        status.put("timestamp", System.currentTimeMillis());

        return Mono.just(status);
    }


    /**
     * Подтверждает обработку сообщения
     */
    public Mono<Boolean> acknowledgeMessage(String clientId, String recordId) {
        return ensureInitialized()
                .flatMap(initialized -> {
                    if (!initialized) {
                        log.warn("Not initialized, cannot acknowledge message");
                        return Mono.just(false);
                    }

                    return redisTemplate.opsForStream()
                            .acknowledge(STATION_EVENTS_STREAM, WEBSOCKET_CONSUMER_GROUP, recordId)
                            .map(count -> count > 0)
                            .doOnSuccess(acked -> {
                                if (acked) {
                                    log.trace("✅ Acknowledged message {} for client {}", recordId, clientId);
                                } else {
                                    log.warn("Failed to acknowledge message {} for client {}", recordId, clientId);
                                }
                            })
                            .onErrorResume(e -> {
                                log.error("❌ Error acknowledging message {} for client {}: {}",
                                        recordId, clientId, e.getMessage());
                                return Mono.just(false);
                            });
                });
    }

    /**
     * Получает информацию о Stream
     */
    public Mono<Map<String, Object>> getStreamInfo() {
        log.debug("Getting stream info for: {}", STATION_EVENTS_STREAM);

        Mono<StreamInfo.XInfoStream> streamInfoMono = redisTemplate.opsForStream()
                .info(STATION_EVENTS_STREAM);

        Flux<StreamInfo.XInfoGroup> groupsFlux = redisTemplate.opsForStream()
                .groups(STATION_EVENTS_STREAM);

        return Mono.zip(streamInfoMono, groupsFlux.collectList())
                .map(tuple -> {
                    StreamInfo.XInfoStream streamInfo = tuple.getT1();
                    List<StreamInfo.XInfoGroup> groupsList = tuple.getT2();

                    Map<String, Object> info = new HashMap<>();
                    info.put("stream", STATION_EVENTS_STREAM);
                    info.put("length", streamInfo.streamLength());
                    info.put("lastGeneratedId", streamInfo.lastGeneratedId());
                    info.put("groups", groupsList.size());

                    List<Map<String, Object>> groupsInfo = groupsList.stream()
                            .map(group -> {
                                Map<String, Object> groupInfo = new HashMap<>();
                                groupInfo.put("name", group.groupName());
                                groupInfo.put("consumers", group.consumerCount());
                                groupInfo.put("pending", group.pendingCount());
                                groupInfo.put("lastDeliveredId", group.lastDeliveredId());
                                return groupInfo;
                            })
                            .toList();
                    info.put("groupsInfo", groupsInfo);

                    log.info("Stream info: length={}, lastId={}, groups={}",
                            streamInfo.streamLength(),
                            streamInfo.lastGeneratedId(),
                            groupsList.size());

                    boolean ourGroupExists = groupsList.stream()
                            .anyMatch(group -> group.groupName().equals(WEBSOCKET_CONSUMER_GROUP));
                    info.put("ourGroupExists", ourGroupExists);
                    info.put("ourGroupName", WEBSOCKET_CONSUMER_GROUP);

                    if (ourGroupExists) {
                        log.info("✅ Our consumer group '{}' exists in stream", WEBSOCKET_CONSUMER_GROUP);
                    } else {
                        log.warn("❓ Our consumer group '{}' not found in stream", WEBSOCKET_CONSUMER_GROUP);
                    }

                    return info;
                })
                .onErrorResume(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("no such key")) {
                        log.warn("Stream '{}' not found.", STATION_EVENTS_STREAM);
                        return Mono.just(Map.of(
                                "stream", STATION_EVENTS_STREAM,
                                "status", "NOT_FOUND",
                                "message", "Stream does not exist",
                                "ourGroupName", WEBSOCKET_CONSUMER_GROUP,
                                "ourGroupExists", false
                        ));
                    }
                    log.error("❌ Error getting stream info: {}", e.getMessage(), e);
                    return Mono.just(Map.of(
                            "stream", STATION_EVENTS_STREAM,
                            "status", "ERROR",
                            "error", e.getMessage(),
                            "ourGroupName", WEBSOCKET_CONSUMER_GROUP,
                            "ourGroupExists", false
                    ));
                });
    }

    /**
     * Проверяет подключение к Redis
     */
    public Mono<Map<String, Object>> checkConnection() {
        log.debug("Checking Redis connection...");

        return redisTemplate.opsForValue()
                .set("websocket-service:health-check",
                        "ok-" + System.currentTimeMillis(),
                        Duration.ofSeconds(10))
                .then(redisTemplate.opsForValue()
                        .get("websocket-service:health-check")
                )
                .map(value -> {
                    log.info("✅ Redis connection check: SUCCESS (value: {})", value);
                    Map<String, Object> result = new HashMap<>();
                    result.put("connected", true);
                    result.put("timestamp", System.currentTimeMillis());
                    result.put("value", value);
                    return result;
                })
                .onErrorResume(e -> {
                    log.error("❌ Redis connection check: FAILED - {}", e.getMessage());
                    Map<String, Object> result = new HashMap<>();
                    result.put("connected", false);
                    result.put("timestamp", System.currentTimeMillis());
                    result.put("error", e.getMessage());
                    return Mono.just(result);
                });
    }

    /**
     * Получает статус сервиса
     */
    public Mono<Map<String, Object>> getServiceStatus() {
        return Mono.zip(
                        checkConnection(),
                        getStreamInfo(),
                        Mono.just(isInitialized.get())
                )
                .map(tuple -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("redis", tuple.getT1());
                    status.put("stream", tuple.getT2());
                    status.put("initialized", tuple.getT3());
                    status.put("timestamp", System.currentTimeMillis());
                    status.put("service", "websocket-service");
                    return status;
                });
    }

    /**
     * Проверяет существование и доступность нашего consumer group
     */
    public Mono<Boolean> isOurConsumerGroupAvailable() {
        return getStreamInfo()
                .map(info -> {
                    Boolean exists = (Boolean) info.get("ourGroupExists");
                    return Boolean.TRUE.equals(exists);
                })
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("Error checking consumer group availability: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    // Внутренний класс для хранения информации о подключении клиента
    @Getter
    @Setter
    @AllArgsConstructor
    private static class ClientConnectionInfo {
        private String consumerName;
        private String connectedAt;
        private volatile boolean active;

        @Override
        public String toString() {
            return "ClientConnectionInfo{" +
                    "consumerName='" + consumerName + '\'' +
                    ", connectedAt='" + connectedAt + '\'' +
                    ", active=" + active +
                    '}';
        }
    }
}
