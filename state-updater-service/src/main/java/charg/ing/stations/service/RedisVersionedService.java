package charg.ing.stations.service;

import charg.ing.stations.dto.StationHourlyTariffEvent;
import charg.ing.stations.dto.StationStateDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisVersionedService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisStreamService redisStreamService;

    private static final String STATION_KEY_PREFIX = "station:state:";
    private static final String ALL_STATIONS_KEY = "public:stations:state";
    private static final Duration KEY_TTL = Duration.ofHours(24);



    /**
     * Атомарно обновляет состояние станции в Redis
     * Теперь также публикует события в Stream
     */
    public Mono<Boolean> updateStationStateIfNewer(StationStateDTO stationState) {
        String key = STATION_KEY_PREFIX + stationState.getStationId();
        String newStateJson;

        try {
            newStateJson = objectMapper.writeValueAsString(stationState);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize station state: {}", stationState.getStationId(), e);
            return Mono.just(false);
        }

        return redisTemplate.opsForValue().get(key)
                .flatMap(existingJson -> {
                    try {
                        StationStateDTO existingState = objectMapper.readValue(existingJson, StationStateDTO.class);

                        if (stationState.getVersion() > existingState.getVersion()) {
                            log.debug("Updating station {} from version {} to {}",
                                    stationState.getStationId(),
                                    existingState.getVersion(),
                                    stationState.getVersion());

                            // Обновляем данные
                            return updateStationInBothPlaces(stationState.getStationId(), newStateJson)
                                    .flatMap(success -> {
                                        if (success) {
                                            // Публикуем событие об обновлении
                                            return redisStreamService.publishStationUpdate(stationState, existingState.getVersion())
                                                    .then(Mono.just(true));
                                        }
                                        return Mono.just(false);
                                    })
                                    .doOnSuccess(success -> {
                                        if (success) {
                                            log.info("Updated station {} to version {}",
                                                    stationState.getStationId(),
                                                    stationState.getVersion());
                                        }
                                    });
                        } else {
                            log.debug("Skipping update for station {}, version {} is not newer",
                                    stationState.getStationId(),
                                    stationState.getVersion());
                            return Mono.just(false);
                        }
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize existing state for station: {}", stationState.getStationId(), e);
                        // Записываем новые данные
                        return updateStationInBothPlaces(stationState.getStationId(), newStateJson)
                                .flatMap(success -> {
                                    if (success) {
                                        // Публикуем событие о создании
                                        return redisStreamService.publishStationUpdate(stationState, null)
                                                .then(Mono.just(true));
                                    }
                                    return Mono.just(false);
                                });
                    }
                })
                .switchIfEmpty(
                        // Создаем новую запись
                        updateStationInBothPlaces(stationState.getStationId(), newStateJson)
                                .flatMap(success -> {
                                    if (success) {
                                        // Публикуем событие о создании
                                        return redisStreamService.publishStationUpdate(stationState, null)
                                                .then(Mono.just(true));
                                    }
                                    return Mono.just(false);
                                })
                                .doOnSuccess(success -> {
                                    if (success) {
                                        log.info("Created new state for station {} version {}",
                                                stationState.getStationId(),
                                                stationState.getVersion());
                                    }
                                })
                )
                .onErrorResume(e -> {
                    log.error("Error updating station state for {}: {}", stationState.getStationId(), e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Обновляет станцию в обоих местах: индивидуальный ключ и общий Hash
     */
    private Mono<Boolean> updateStationInBothPlaces(String stationId, String stationJson) {
        String individualKey = STATION_KEY_PREFIX + stationId;

        // 1. Обновляем индивидуальный ключ
        Mono<Boolean> updateIndividual = redisTemplate.opsForValue()
                .set(individualKey, stationJson, KEY_TTL);

        // 2. Обновляем в общем Hash
        Mono<Boolean> updateInHash = redisTemplate.opsForHash()
                .put(ALL_STATIONS_KEY, stationId, stationJson)
                .then(Mono.just(true));

        // 3. Устанавливаем TTL для Hash (если нужно)
        Mono<Boolean> setHashTtl = redisTemplate.expire(ALL_STATIONS_KEY, KEY_TTL)
                .defaultIfEmpty(false);

        // Выполняем все операции
        return Mono.zip(updateIndividual, updateInHash, setHashTtl)
                .map(tuple -> tuple.getT1() && tuple.getT2())
                .onErrorResume(e -> {
                    log.error("Error updating station in both places {}: {}", stationId, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Получает состояние станции по ID
     * Теперь пробует получить из общего Hash сначала
     */
    public Mono<StationStateDTO> getStationState(String stationId) {
        // Сначала пробуем получить из общего Hash
        return redisTemplate.opsForHash()
                .get(ALL_STATIONS_KEY, stationId)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue((String) json, StationStateDTO.class));
                    } catch (Exception e) {
                        log.warn("Failed to get station {} from hash, trying individual key", stationId);
                        // Если не получилось из Hash, пробуем индивидуальный ключ
                        return getStationStateFromIndividualKey(stationId);
                    }
                })
                .switchIfEmpty(
                        // Если в Hash нет, пробуем индивидуальный ключ
                        getStationStateFromIndividualKey(stationId)
                );
    }

    /**
     * Получает станцию из индивидуального ключа
     */
    private Mono<StationStateDTO> getStationStateFromIndividualKey(String stationId) {
        String key = STATION_KEY_PREFIX + stationId;

        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, StationStateDTO.class));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize state for station: {}", stationId, e);
                        return Mono.empty();
                    }
                });
    }

    /**
     * Получить ВСЕ станции из общего Hash одним запросом
     * Самый эффективный способ - получить весь Hash сразу
     */
    public Mono<Map<String, StationStateDTO>> getAllStationsFromHash() {
        log.info("Getting all stations from hash: {}", ALL_STATIONS_KEY);

        return redisTemplate.opsForHash()
                .entries(ALL_STATIONS_KEY)
                .collectMap(
                        entry -> (String) entry.getKey(),
                        entry -> {
                            try {
                                return objectMapper.readValue((String) entry.getValue(), StationStateDTO.class);
                            } catch (JsonProcessingException e) {
                                log.error("Failed to deserialize station from hash: {}", entry.getKey(), e);
                                return null;
                            }
                        }
                )
                .flatMap(map -> {
                    // Убираем null значения (если были ошибки десериализации)
                    map.values().removeIf(value -> value == null);
                    log.debug("Retrieved {} stations from hash", map.size());
                    return Mono.just(map);
                })
                .onErrorResume(e -> {
                    log.error("Error getting all stations from hash: {}", e.getMessage());
                    return Mono.just(Map.of());
                });
    }

    /**
     * Получить все станции в виде Flux (по одной)
     */
    public Flux<StationStateDTO> getAllStationsAsFlux() {
        return redisTemplate.opsForHash()
                .values(ALL_STATIONS_KEY)
                .flatMap(value -> {
                    try {
                        return Mono.just(objectMapper.readValue((String) value, StationStateDTO.class));
                    } catch (Exception e) {
                        log.warn("Failed to deserialize station from hash value");
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error streaming stations from hash: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Инициализирует общий Hash со всеми станциями
     * Используется при старте/перезапуске
     */
    public Mono<Boolean> initializeAllStationsHash(Map<String, String> stationsData) {
        if (stationsData == null || stationsData.isEmpty()) {
            log.warn("No station data provided for hash initialization");
            return Mono.just(false);
        }

        log.info("Initializing stations hash with {} stations", stationsData.size());

        // 1. Очищаем старый Hash
        return redisTemplate.delete(ALL_STATIONS_KEY)
                .then(
                        // 2. Записываем все станции в Hash
                        redisTemplate.opsForHash()
                                .putAll(ALL_STATIONS_KEY, stationsData)
                                .then(Mono.just(true))
                )
                // 3. Устанавливаем TTL
                .flatMap(success ->
                        redisTemplate.expire(ALL_STATIONS_KEY, KEY_TTL)
                                .then(Mono.just(success))
                )
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Successfully initialized stations hash with {} entries", stationsData.size());
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error initializing stations hash: {}", e.getMessage());
                    return Mono.just(false);
                });
    }


    /**
     * Обновляет тарифы станции в Redis (HSET + individual key) и возвращает обновлённое состояние
     */
    public Mono<StationStateDTO> updateStationTariffs(StationHourlyTariffEvent tariff) {
        String stationId = tariff.getStationId();

        return getStationState(stationId)
                .flatMap(state -> {
                    state.setKwCost(tariff.getKwCost());
                    state.setBookingMinuteCost(tariff.getBookingMinuteCost());

                    String updatedJson;
                    try {
                        updatedJson = objectMapper.writeValueAsString(state);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize updated state for station: {}", stationId, e);
                        return Mono.empty();
                    }

                    return updateStationInBothPlaces(stationId, updatedJson)
                            .filter(Boolean::booleanValue)
                            .map(ok -> state);
                })
                .doOnSuccess(state -> {
                    if (state != null) {
                        log.debug("Updated tariffs for station {} kwCost={} bookingMinuteCost={}",
                                stationId, tariff.getKwCost(), tariff.getBookingMinuteCost());
                    } else {
                        log.warn("Station {} not found in Redis, tariff update skipped", stationId);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error updating tariffs for station {}: {}", stationId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Удаляет станцию и публикует событие
     */
    public Mono<Boolean> deleteStationState(String stationId) {
        String individualKey = STATION_KEY_PREFIX + stationId;

        // Удаляем из обоих мест
        Mono<Long> deleteIndividual = redisTemplate.delete(individualKey);
        Mono<Long> deleteFromHash = redisTemplate.opsForHash().remove(ALL_STATIONS_KEY, stationId);

        return Mono.zip(deleteIndividual, deleteFromHash)
                .flatMap(tuple -> {
                    boolean deleted = tuple.getT1() > 0 || tuple.getT2() > 0;

                    if (deleted) {
                        // Публикуем событие об удалении
                        return redisStreamService.publishStationDeletion(stationId)
                                .then(Mono.just(true));
                    }
                    return Mono.just(false);
                })
                .doOnSuccess(deleted -> {
                    if (deleted) {
                        log.info("Deleted station {} and published deletion event", stationId);
                    } else {
                        log.debug("Station {} not found in Redis", stationId);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error deleting station {}: {}", stationId, error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Получить количество станций в Hash
     */
    public Mono<Long> getStationsCount() {
        return redisTemplate.opsForHash()
                .size(ALL_STATIONS_KEY)
                .doOnNext(count -> log.debug("Total stations in hash: {}", count))
                .onErrorResume(e -> {
                    log.error("Error counting stations in hash: {}", e.getMessage());
                    return Mono.just(0L);
                });
    }

    /**
     * Проверяет существование Hash
     */
    public Mono<Boolean> isHashInitialized() {
        return redisTemplate.hasKey(ALL_STATIONS_KEY)
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("Error checking hash existence: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Удаляет ВСЕ станции (индивидуальные ключи и Hash)
     */
    public Mono<Long> deleteAllStations() {
        log.warn("Deleting ALL stations from Redis");

        // 1. Удаляем общий Hash
        Mono<Long> deleteHash = redisTemplate.delete(ALL_STATIONS_KEY);

        String deleteStationKey = STATION_KEY_PREFIX + "*";
        ScanOptions scanOptions = ScanOptions.scanOptions().match(deleteStationKey).build();

        // 2. Находим и удаляем все индивидуальные ключи
        Mono<Long> deleteIndividualKeys = redisTemplate.scan(scanOptions)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(0L);
                    }
                    return redisTemplate.delete(keys.toArray(new String[0]));
                });

        return Mono.zip(deleteHash, deleteIndividualKeys)
                .map(tuple -> tuple.getT1() + tuple.getT2())
                .doOnSuccess(count ->
                        log.warn("Deleted {} Redis entries (hash + individual keys)", count)
                )
                .onErrorResume(e -> {
                    log.error("Error deleting all stations: {}", e.getMessage());
                    return Mono.just(0L);
                });
    }
}