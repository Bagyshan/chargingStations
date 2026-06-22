package charg.ing.stations.service;

import charg.ing.stations.client.StationControlServiceClient;
import charg.ing.stations.dto.StationStateDTO;
import charg.ing.stations.service.util.DataInitializationCompletedEvent;
import charg.ing.stations.service.util.KafkaMessageProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializationService {

    private final StationControlServiceClient stationControlServiceClient;
    private final RedisVersionedService redisVersionedService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final KafkaMessageProcessor kafkaMessageProcessor;
    private final ApplicationEventPublisher publisher;
    private final KafkaConsumerLifecycleManager kafkaLifecycleManager;
    private final GeoDataService geoDataService;
    private final ObjectMapper objectMapper;
    private final RedisStreamService redisStreamService;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
//    private final AtomicBoolean isKafkaProcessingEnabled = new AtomicBoolean(false);

    private static final String INITIALIZATION_LOCK_KEY = "state-updater:initialization:lock";
    private static final String INITIALIZATION_FLAG_KEY = "state-updater:initialization:completed";
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Инициализация при старте приложения
     */
    @PostConstruct
    public void init() {
        log.info("Starting data initialization service...");
    }

    /**
     * Обработчик события готовности приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        kafkaLifecycleManager.stop();

        log.info("Application is ready, starting data synchronization...");

        // Инициализируем Stream перед загрузкой данных
        initializeStream()
                .then(checkAndInitializeData())
                .subscribe(
                        success -> {
                            if (success) {
                                log.info("Data initialization completed successfully");
                                publisher.publishEvent(new DataInitializationCompletedEvent());
                            } else {
                                log.error("Data initialization failed");
                            }
                        },
                        error -> log.error(
                                "Error during data initialization: {}",
                                error.getMessage(),
                                error
                        )
                );
    }
//    @EventListener(ApplicationReadyEvent.class)
//    public void onApplicationReady() {
//        kafkaLifecycleManager.stop();
//
//        log.info("Application is ready, starting data synchronization...");
//
//        // Сначала проверяем, может Hash уже инициализирован
//        checkAndInitializeData()
//                .subscribe(
//                        success -> {
//                            if (success) {
//                                log.info("Data initialization completed successfully");
//                                publisher.publishEvent(new DataInitializationCompletedEvent());
//                            } else {
//                                log.error("Data initialization failed");
//                            }
//                        },
//                        error -> log.error(
//                                "Error during data initialization: {}",
//                                error.getMessage(),
//                                error
//                        )
//                );
//    }


    /**
     * Инициализирует Redis Stream
     */
    private Mono<Boolean> initializeStream() {
        return redisStreamService.initializeStream()
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Redis Stream initialized successfully");
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to initialize Redis Stream: {}", e.getMessage());
                    return Mono.just(false);
                });
    }


    /**
     * Проверяет и инициализирует данные при необходимости
     */
    private Mono<Boolean> checkAndInitializeData() {
        return redisVersionedService.isHashInitialized()
                .flatMap(hashExists -> {
                    if (hashExists) {
                        log.info("Stations hash already exists, skipping full initialization");
                        isInitialized.set(true);
                        return Mono.just(true);
                    } else {
                        log.info("Stations hash not found, performing full initialization");
                        return initializeData();
                    }
                });
    }

    /**
     * Основной метод инициализации данных
     */
    public Mono<Boolean> initializeData() {
        if (isInitialized.get()) {
            log.info("Data already initialized");
            return Mono.just(true);
        }

        return acquireInitializationLock()
                .flatMap(lockAcquired -> {
                    if (!lockAcquired) {
                        log.info("Initialization lock already acquired by another instance, waiting...");
                        return waitForInitialization();
                    }

                    log.info("Acquired initialization lock, starting data load...");
                    return loadAllStations()
                            .flatMap(stats -> {
                                log.info("Successfully loaded {} stations, {} with geo data, hash initialized: {}",
                                        stats.totalStations, stats.stationsWithGeoData, stats.hashInitialized);
                                return markInitializationAsCompleted()
                                        .doOnSuccess(success -> {
                                            if (success) {
                                                isInitialized.set(true);
                                                log.info("Initialization marked as completed");
                                            }
                                        });
                            })
                            .doFinally(signal -> releaseInitializationLock().subscribe())
                            .onErrorResume(error -> {
                                log.error("Error during initialization: {}", error.getMessage(), error);
                                return releaseInitializationLock()
                                        .then(Mono.just(false));
                            });
                });
    }
//    public Mono<Boolean> initializeData() {
//        if (isInitialized.get()) {
//            log.info("Data already initialized");
//            return Mono.just(true);
//        }
//
//        return acquireInitializationLock()
//                .flatMap(lockAcquired -> {
//                    if (!lockAcquired) {
//                        log.info("Initialization lock already acquired by another instance, waiting...");
//                        return waitForInitialization();
//                    }
//
//                    log.info("Acquired initialization lock, starting data load...");
//                    return loadAllStations()
//                            .flatMap(loadedStats -> {
//                                log.info("Successfully loaded {} stations, {} with geo data",
//                                        loadedStats.totalStations, loadedStats.stationsWithGeoData);
//                                return markInitializationAsCompleted()
//                                        .doOnSuccess(success -> {
//                                            if (success) {
//                                                isInitialized.set(true);
//                                                log.info("Initialization marked as completed");
//                                            }
//                                        });
//                            })
//                            .doFinally(signal -> releaseInitializationLock().subscribe())
//                            .onErrorResume(error -> {
//                                log.error("Error during initialization: {}", error.getMessage(), error);
//                                return releaseInitializationLock()
//                                        .then(Mono.just(false));
//                            });
//                });
//    }

    /**
     * Загружает все станции из station-control-service
     */
    private Mono<LoadStats> loadAllStations() {
        return stationControlServiceClient.getAllStations()
                .doOnSubscribe(s ->
                        log.info("Fetching stations from station-control-service...")
                )
                .collectList()
                .flatMap(stationDTOs -> {
                    log.info("Received {} stations from station-control-service", stationDTOs.size());

                    // 1. Подготавливаем данные для Hash
                    Map<String, String> hashData = new HashMap<>();

                    // 2. Обрабатываем каждую станцию
                    return Flux.fromIterable(stationDTOs)
                            .flatMap(stationDTO -> {
                                StationStateDTO stationState = StationStateDTO.fromStationDTO(stationDTO);

                                try {
                                    // Добавляем в данные для Hash
                                    String stationJson = objectMapper.writeValueAsString(stationState);
                                    hashData.put(stationState.getStationId(), stationJson);

                                    // Загружаем в Redis через стандартный метод
                                    return redisVersionedService.updateStationStateIfNewer(stationState)
                                            .flatMap(stationLoaded -> {
                                                // Загружаем геоданные если есть
                                                if (stationDTO.getGeolocation() != null &&
                                                        stationDTO.getGeolocation().getLatitude() != null &&
                                                        stationDTO.getGeolocation().getLongitude() != null) {

                                                    return geoDataService.saveStationGeoData(stationDTO)
                                                            .map(geoLoaded -> new StationLoadResult(
                                                                    stationState.getStationId(),
                                                                    stationLoaded,
                                                                    geoLoaded
                                                            ));
                                                } else {
                                                    return Mono.just(new StationLoadResult(
                                                            stationState.getStationId(),
                                                            stationLoaded,
                                                            false
                                                    ));
                                                }
                                            });
                                } catch (JsonProcessingException e) {
                                    log.error("Failed to serialize station {}: {}",
                                            stationState.getStationId(), e.getMessage());
                                    return Mono.just(new StationLoadResult(
                                            stationState.getStationId(),
                                            false,
                                            false
                                    ));
                                }
                            })
                            .collectList()
                            .flatMap(loadResults -> {
                                // 3. Инициализируем общий Hash ВСЕМИ станциями сразу
                                return redisVersionedService.initializeAllStationsHash(hashData)
                                        .map(hashInitialized -> {
                                            long stationsLoaded = loadResults.stream()
                                                    .filter(r -> r.stationDataLoaded)
                                                    .count();
                                            long geoDataLoaded = loadResults.stream()
                                                    .filter(r -> r.geoDataLoaded)
                                                    .count();

                                            log.info("Data load statistics:");
                                            log.info("- Individual stations loaded: {}/{}", stationsLoaded, loadResults.size());
                                            log.info("- Geo data loaded: {}/{}", geoDataLoaded, loadResults.size());
                                            log.info("- Hash initialized with {} stations: {}", hashData.size(), hashInitialized);

                                            return new LoadStats(
                                                    loadResults.size(),
                                                    (int) stationsLoaded,
                                                    (int) geoDataLoaded,
                                                    hashInitialized
                                            );
                                        });
                            });
                });
    }
//    private Mono<LoadStats> loadAllStations() {
//        return stationControlServiceClient.getAllStations()
//                .doOnSubscribe(s ->
//                        log.info("Fetching stations from station-control-service...")
//                )
//                .flatMap(stationDTO -> {
//                    // 1. Загружаем основные данные станции в Redis базу 0
//                    StationStateDTO stationState = StationStateDTO.fromStationDTO(stationDTO);
//                    Mono<Boolean> loadMainData = redisVersionedService
//                            .updateStationStateIfNewer(stationState);
//
//                    // 2. Загружаем геоданные в Redis базу 1 (если есть координаты)
//                    Mono<Boolean> loadGeoData;
//                    if (stationDTO.getGeolocation() != null &&
//                            stationDTO.getGeolocation().getLatitude() != null &&
//                            stationDTO.getGeolocation().getLongitude() != null) {
//
//                        loadGeoData = geoDataService.saveStationGeoData(stationDTO)
//                                .doOnNext(success -> {
//                                    if (success) {
//                                        log.debug("Loaded geo data for station {}",
//                                                stationState.getStationId());
//                                    }
//                                });
//                    } else {
//                        loadGeoData = Mono.just(false)
//                                .doOnNext(ignored ->
//                                        log.debug("Station {} has no geo coordinates",
//                                                stationState.getStationId()));
//                    }
//
//                    // Выполняем обе операции параллельно
//                    return Mono.zip(loadMainData, loadGeoData)
//                            .map(tuple -> new StationLoadResult(
//                                    stationState.getStationId(),
//                                    tuple.getT1(),
//                                    tuple.getT2()
//                            ));
//                })
//                .collectList()
//                .map(results -> {
//                    long stationsLoaded = results.stream()
//                            .filter(r -> r.stationDataLoaded)
//                            .count();
//                    long geoDataLoaded = results.stream()
//                            .filter(r -> r.geoDataLoaded)
//                            .count();
//
//                    log.info("Data load statistics:");
//                    log.info("- Stations loaded: {}/{}", stationsLoaded, results.size());
//                    log.info("- Geo data loaded: {}/{}", geoDataLoaded, results.size());
//
//                    return new LoadStats(results.size(), (int) stationsLoaded, (int) geoDataLoaded);
//                });
//    }

    /**
     * Пытается получить блокировку для инициализации
     */
    private Mono<Boolean> acquireInitializationLock() {
        return redisTemplate.opsForValue()
                .setIfAbsent(INITIALIZATION_LOCK_KEY, "locked", LOCK_TIMEOUT)
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.error("Error acquiring initialization lock: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Освобождает блокировку инициализации
     */
    private Mono<Boolean> releaseInitializationLock() {
        return redisTemplate.delete(INITIALIZATION_LOCK_KEY)
                .map(count -> count > 0)
                .onErrorResume(error -> {
                    log.error("Error releasing initialization lock: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Помечает инициализацию как завершенную
     */
    private Mono<Boolean> markInitializationAsCompleted() {
        return redisTemplate.opsForValue()
                .set(INITIALIZATION_FLAG_KEY, "true", Duration.ofDays(1))
                .onErrorResume(error -> {
                    log.error("Error marking initialization as completed: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Ожидает завершения инициализации другим экземпляром
     */
    private Mono<Boolean> waitForInitialization() {
        return redisTemplate.opsForValue()
                .get(INITIALIZATION_FLAG_KEY)
                .repeatWhenEmpty(10, repeat -> repeat.delayElements(Duration.ofSeconds(3)))
                .flatMap(flag -> {
                    if ("true".equals(flag)) {
                        log.info("Initialization completed by another instance");
                        isInitialized.set(true);
                        return Mono.just(true);
                    }
                    log.warn("Initialization flag not found, retrying...");
                    return Mono.empty();
                })
                .timeout(Duration.ofMinutes(2))
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.error("Error waiting for initialization: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Проверяет, завершена ли инициализация
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }

    /**
     * Проверяет, включена ли обработка Kafka
     */
//    public boolean isKafkaProcessingEnabled() {
//        return isKafkaProcessingEnabled.get();
//    }

    /**
     * Перезагружает данные (для восстановления после сбоя)
     */
    public Mono<Boolean> reloadData() {
        log.info("Reloading data from station-control-service...");
        isInitialized.set(false);
        return geoDataService.clearAllGeoData()
                .doOnSuccess(count -> log.info("Cleared {} geo data records", count))
                // 2. Загружаем новые данные
                .then(initializeData());
    }

    // Вспомогательные классы для статистики
    private static class StationLoadResult {
        final String stationId;
        final boolean stationDataLoaded;
        final boolean geoDataLoaded;

        StationLoadResult(String stationId, boolean stationDataLoaded, boolean geoDataLoaded) {
            this.stationId = stationId;
            this.stationDataLoaded = stationDataLoaded;
            this.geoDataLoaded = geoDataLoaded;
        }
    }

    private static class LoadStats {
        final int totalStations;
        final int stationsLoaded;
        final int stationsWithGeoData;
        final boolean hashInitialized;

        LoadStats(int totalStations, int stationsLoaded, int stationsWithGeoData, boolean hashInitialized) {
            this.totalStations = totalStations;
            this.stationsLoaded = stationsLoaded;
            this.stationsWithGeoData = stationsWithGeoData;
            this.hashInitialized = hashInitialized;
        }
    }
}