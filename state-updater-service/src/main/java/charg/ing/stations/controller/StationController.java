package charg.ing.stations.controller;


import charg.ing.stations.dto.AllStationsResponse;
import charg.ing.stations.dto.StationStateDTO;
import charg.ing.stations.dto.StationStateWithDistance;
import charg.ing.stations.service.GeoDataService;
import charg.ing.stations.service.RedisVersionedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/cached-stations")
@RequiredArgsConstructor
@Slf4j
public class StationController {

    private final RedisVersionedService redisVersionedService;
    private final GeoDataService geoDataService;

    /**
     * Получить ВСЕ станции из общего Hash (самый быстрый способ)
     * GET /api/cached-stations
     *
     * Возвращает Map где ключ - stationId, значение - данные станции
     */
    /**
     * Получить все станции.
     * Если переданы longitude и latitude — возвращает с расстоянием от точки и сортировкой.
     * Если параметры не переданы — возвращает все станции без расстояния (как раньше).
     */
    @GetMapping
    public Mono<ResponseEntity<AllStationsResponse>> getAllStations(
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false, defaultValue = "false") boolean availableOnly,
            @RequestParam(required = false, defaultValue = "false") boolean freeConnectorsOnly,
            @RequestParam(required = false) List<Integer> connectorTypeIds,
            @RequestParam(required = false) Double minPower,
            @RequestParam(required = false) Double maxPower,
            @RequestParam(required = false) BigDecimal minKwCost,
            @RequestParam(required = false) BigDecimal maxKwCost,
            @RequestParam(required = false) BigDecimal minBookingMinuteCost,
            @RequestParam(required = false) BigDecimal maxBookingMinuteCost) {

        Predicate<StationStateDTO> filter = buildFilter(
                availableOnly, freeConnectorsOnly, connectorTypeIds,
                minPower, maxPower, minKwCost, maxKwCost,
                minBookingMinuteCost, maxBookingMinuteCost);

        // Проверка на частичную передачу параметров
        if (longitude == null && latitude == null) {
            // Режим без расстояния. radiusKm без координат игнорируется (фильтровать не от чего).
            log.info("Getting ALL stations from Redis Hash (without distance), availableOnly={}, freeConnectorsOnly={}, connectorTypeIds={}",
                    availableOnly, freeConnectorsOnly, connectorTypeIds);
            return redisVersionedService.getAllStationsFromHash()
                    .flatMap(stations -> {
                        // Преобразуем Map<String, StationStateDTO> в List<StationStateWithDistance>
                        List<StationStateWithDistance> stationList = stations.values().stream()
                                .filter(filter)
                                .map(dto -> new StationStateWithDistance(dto, null)) // distance = null
                                .toList();
                        return Mono.just(AllStationsResponse.of(stationList));
                    })
                    .map(ResponseEntity::ok);
        } else if (longitude != null && latitude != null) {
            // Режим с расстоянием
            log.info("Getting ALL stations with distance from point ({}, {}), radiusKm={}, availableOnly={}, freeConnectorsOnly={}, connectorTypeIds={}",
                    longitude, latitude, radiusKm, availableOnly, freeConnectorsOnly, connectorTypeIds);
            return getAllStationsWithDistance(longitude, latitude, radiusKm, filter)
                    .map(ResponseEntity::ok);
        } else {
            // Передан только один параметр — ошибка. Возвращаем 400 Bad Request.
            return Mono.just(ResponseEntity.badRequest().body(
                    AllStationsResponse.of("Both longitude and latitude must be provided together")
            ));
        }
    }

    /**
     * Собирает предикат фильтрации станции по переданным query-параметрам.
     * null/пустые параметры означают «фильтр не задан» (станция проходит).
     *
     * Семантика коннекторных фильтров: если заданы freeConnectorsOnly и/или connectorTypeIds,
     * станция проходит только если у неё есть хотя бы один коннектор, удовлетворяющий ВСЕМ заданным
     * коннекторным условиям одновременно (т.е. «свободный коннектор нужного типа»).
     */
    private Predicate<StationStateDTO> buildFilter(
            boolean availableOnly, boolean freeConnectorsOnly, List<Integer> connectorTypeIds,
            Double minPower, Double maxPower, BigDecimal minKwCost, BigDecimal maxKwCost,
            BigDecimal minBookingMinuteCost, BigDecimal maxBookingMinuteCost) {

        Set<Integer> typeIds = (connectorTypeIds == null || connectorTypeIds.isEmpty())
                ? null : new HashSet<>(connectorTypeIds);

        return dto -> {
            if (availableOnly && !isAvailable(dto)) return false;

            // power хранится строкой (например "60" или "60 kW") — извлекаем число (кВт).
            Double power = parsePower(dto.getPower());
            if (minPower != null && (power == null || power < minPower)) return false;
            if (maxPower != null && (power == null || power > maxPower)) return false;

            if (!inRange(dto.getKwCost(), minKwCost, maxKwCost)) return false;
            if (!inRange(dto.getBookingMinuteCost(), minBookingMinuteCost, maxBookingMinuteCost)) return false;

            // Коннекторные фильтры: хотя бы один коннектор удовлетворяет всем заданным условиям.
            if (freeConnectorsOnly || typeIds != null) {
                List<StationStateDTO.ConnectorState> connectors = dto.getConnectors();
                if (connectors == null || connectors.isEmpty()) return false;
                boolean anyMatch = connectors.stream().anyMatch(c -> {
                    boolean typeOk = typeIds == null
                            || (c.getConnectorType() != null && typeIds.contains(c.getConnectorType().getId()));
                    boolean freeOk = !freeConnectorsOnly || "Available".equalsIgnoreCase(c.getStatus());
                    return typeOk && freeOk;
                });
                if (!anyMatch) return false;
            }

            return true;
        };
    }

    /** Проверка попадания значения в диапазон [min, max]; null-границы не ограничивают. */
    private boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min != null && (value == null || value.compareTo(min) < 0)) return false;
        if (max != null && (value == null || value.compareTo(max) > 0)) return false;
        return true;
    }

    /** Извлекает числовое значение мощности (кВт) из строки power. Возвращает null, если число не найдено. */
    private static Double parsePower(String power) {
        if (power == null) return null;
        Matcher m = POWER_PATTERN.matcher(power);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static final Pattern POWER_PATTERN = Pattern.compile("[0-9]+(?:[.,][0-9]+)?");
//    @GetMapping
//    public Mono<ResponseEntity<Map<String, StationStateDTO>>> getAllStations(
//            @RequestParam(required = false) Double longitude,
//            @RequestParam(required = false) Double latitude) {
//        log.info("Getting ALL stations from Redis Hash");
//
//        // Проверка на частичную передачу параметров
//        if (longitude == null && latitude == null) {
//            // Режим без расстояния
//            log.info("Getting ALL stations from Redis Hash (without distance)");
//            return redisVersionedService.getAllStationsFromHash()
//                    .map(ResponseEntity::ok)
//                    .defaultIfEmpty(ResponseEntity.ok(Map.of()));
//        } else if (longitude != null && latitude != null) {
//            // Режим с расстоянием
//            log.info("Getting ALL stations with distance from point ({}, {})", longitude, latitude);
//            return getAllStationsWithDistance(longitude, latitude);
//        } else {
//            // Передан только один параметр — ошибка
//            return Mono.just(ResponseEntity.badRequest()
//                    .body("Both longitude and latitude must be provided together"));
//        }
////        return redisVersionedService.getAllStationsFromHash()
////                .map(ResponseEntity::ok)
////                .defaultIfEmpty(ResponseEntity.ok(Map.of()))
////                .doOnSuccess(response ->
////                        log.debug("Retrieved {} stations from hash",
////                                response.getBody() != null ? response.getBody().size() : 0)
////                );
//    }

    /** Станция доступна для карты: на связи (online) и не выведена оператором из эксплуатации. */
    private boolean isAvailable(StationStateDTO dto) {
        boolean online = !Boolean.FALSE.equals(dto.getOnline());
        boolean inService = dto.getServiceStatus() == null || "IN_SERVICE".equalsIgnoreCase(dto.getServiceStatus());
        return online && inService;
    }

    private Mono<AllStationsResponse> getAllStationsWithDistance(double longitude, double latitude,
                                                                Double radiusKm, Predicate<StationStateDTO> filter) {
        Mono<Map<String, StationStateDTO>> stationsMapMono = redisVersionedService.getAllStationsFromHash();
        Flux<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResultsFlux = geoDataService.findAllStationsWithDistance(longitude, latitude);

        return Mono.zip(geoResultsFlux.collectList(), stationsMapMono)
                .map(tuple -> {
                    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResults = tuple.getT1();
                    Map<String, StationStateDTO> stations = tuple.getT2();

                    Map<String, StationStateWithDistance> resultMap = new LinkedHashMap<>();
                    for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : geoResults) {
                        String stationId = geoResult.getContent().getName();
                        double distance = geoResult.getDistance().getValue();
                        // Геоиндекс отсортирован по возрастанию расстояния — после первой станции
                        // за пределами радиуса можно прекратить обход.
                        if (radiusKm != null && distance > radiusKm) {
                            break;
                        }
                        StationStateDTO stationData = stations.get(stationId);
                        if (stationData != null) {
                            if (!filter.test(stationData)) {
                                continue; // станция не прошла фильтры
                            }
                            resultMap.put(stationId, new StationStateWithDistance(stationData, distance));
                        } else {
                            log.warn("Station {} found in geo index but not in hash", stationId);
                        }
                    }

                    if (stations.size() > resultMap.size()) {
                        log.warn("Some stations are missing in geo index: {} in hash vs {} in geo",
                                stations.size(), resultMap.size());
                    }

                    return AllStationsResponse.of(new ArrayList<>(resultMap.values()));
                })
                .onErrorResume(e -> {
                    log.error("Error getting stations with distance: {}", e.getMessage(), e);
                    return Mono.just(AllStationsResponse.of(String.valueOf(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())));
                });
    }

//    private Mono<ResponseEntity<Map<String, StationStateWithDistance>>> getAllStationsWithDistance(
//            double longitude, double latitude) {
//
//        Flux<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResultsFlux = geoDataService.findAllStationsWithDistance(longitude, latitude);
//        Mono<Map<String, StationStateDTO>> stationsMapMono = redisVersionedService.getAllStationsFromHash();
//
//        return Mono.zip(geoResultsFlux.collectList(), stationsMapMono)
//                .map(tuple -> {
//                    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResults = tuple.getT1();
//                    Map<String, StationStateDTO> stations = tuple.getT2();
//
//                    Map<String, StationStateWithDistance> resultMap = new LinkedHashMap<>();
//                    for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : geoResults) {
//                        String stationId = geoResult.getContent().getName();
//                        double distance = geoResult.getDistance().getValue(); // в километрах
//                        StationStateDTO stationData = stations.get(stationId);
//                        if (stationData != null) {
//                            resultMap.put(stationId, new StationStateWithDistance(stationData, distance));
//                        } else {
//                            log.warn("Station {} found in geo index but not in hash", stationId);
//                        }
//                    }
//
//                    if (stations.size() > resultMap.size()) {
//                        log.warn("Some stations are missing in geo index: {} in hash vs {} in geo",
//                                stations.size(), resultMap.size());
//                    }
//
//                    return ResponseEntity.ok(resultMap);
//                })
//                .onErrorResume(e -> {
//                    log.error("Error getting stations with distance: {}", e.getMessage(), e);
//                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
//                })
//                .defaultIfEmpty(ResponseEntity.ok(Map.of()));
//    }

    /**
     * Обогатить переданный список станций данными из кэша (для экрана «Избранное» и т.п.).
     * POST /api/cached-stations/by-ids?longitude=&latitude=
     * Тело: ["CB-1", "CB-2", ...]
     *
     * Возвращает те же StationStateWithDistance, что и общий список: если переданы координаты —
     * с расстоянием и сортировкой по близости; иначе — без расстояния, в порядке переданных ID.
     * Несуществующие/отсутствующие в кэше ID просто пропускаются.
     */
    @PostMapping("/by-ids")
    public Mono<ResponseEntity<AllStationsResponse>> getStationsByIds(
            @RequestBody List<String> stationIds,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double latitude) {

        if (stationIds == null || stationIds.isEmpty()) {
            return Mono.just(ResponseEntity.ok(AllStationsResponse.of(List.<StationStateWithDistance>of())));
        }
        if ((longitude == null) != (latitude == null)) {
            return Mono.just(ResponseEntity.badRequest().body(
                    AllStationsResponse.of("Both longitude and latitude must be provided together")));
        }

        java.util.LinkedHashSet<String> requested = new java.util.LinkedHashSet<>(stationIds);
        log.info("Enriching {} stations by ids (withDistance={})", requested.size(), longitude != null);

        if (longitude == null) {
            // Без расстояния — сохраняем порядок переданных ID.
            return redisVersionedService.getAllStationsFromHash()
                    .map(stations -> {
                        List<StationStateWithDistance> result = new ArrayList<>();
                        for (String id : requested) {
                            StationStateDTO dto = stations.get(id);
                            if (dto != null) {
                                result.add(new StationStateWithDistance(dto, null));
                            }
                        }
                        return ResponseEntity.ok(AllStationsResponse.of(result));
                    });
        }

        // С расстоянием — переиспользуем гео-индекс (сортировка по близости), фильтруем по запрошенным ID.
        Flux<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResultsFlux =
                geoDataService.findAllStationsWithDistance(longitude, latitude);

        return Mono.zip(geoResultsFlux.collectList(), redisVersionedService.getAllStationsFromHash())
                .map(tuple -> {
                    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResults = tuple.getT1();
                    Map<String, StationStateDTO> stations = tuple.getT2();

                    Map<String, StationStateWithDistance> resultMap = new LinkedHashMap<>();
                    for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : geoResults) {
                        String stationId = geoResult.getContent().getName();
                        if (!requested.contains(stationId)) {
                            continue;
                        }
                        StationStateDTO stationData = stations.get(stationId);
                        if (stationData != null) {
                            double distance = geoResult.getDistance().getValue();
                            resultMap.put(stationId, new StationStateWithDistance(stationData, distance));
                        }
                    }
                    return ResponseEntity.ok(AllStationsResponse.of(new ArrayList<>(resultMap.values())));
                })
                .onErrorResume(e -> {
                    log.error("Error enriching stations by ids: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(AllStationsResponse.of("Internal error")));
                });
    }

    /**
     * Получить все станции в виде списка (Flux)
     * GET /api/cached-stations/stream
     */
    @GetMapping("/stream")
    public Flux<StationStateDTO> getAllStationsAsStream() {
        log.info("Streaming all stations from Redis");

        return redisVersionedService.getAllStationsAsFlux()
                .doOnSubscribe(s -> log.debug("Starting stream of stations"))
                .doOnComplete(() -> log.debug("Stream completed"));
    }

    /**
     * Получить конкретную станцию по ID
     * GET /api/cached-stations/{stationId}
     */
    @GetMapping("/{stationId}")
    public Mono<ResponseEntity<StationStateDTO>> getStation(@PathVariable String stationId) {
        log.info("Getting station from Redis: {}", stationId);

        return redisVersionedService.getStationState(stationId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Проверить состояние Hash
     * GET /api/cached-stations/hash-status
     */
    @GetMapping("/hash-status")
    public Mono<ResponseEntity<Map<String, Object>>> getHashStatus() {
        log.info("Checking stations hash status");

        return redisVersionedService.isHashInitialized()
                .flatMap(hashExists ->
                        redisVersionedService.getStationsCount()
                                .map(count -> {
                                    Map<String, Object> response = Map.of(
                                            "hashExists", hashExists,
                                            "stationsCount", count,
                                            "hashKey", "public:stations:state",
                                            "timestamp", Instant.now().toString()
                                    );
                                    return ResponseEntity.ok(response);
                                })
                )
                .onErrorResume(e -> {
                    log.error("Error checking hash status: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * Получить количество станций
     * GET /api/cached-stations/count
     */
    @GetMapping("/count")
    public Mono<ResponseEntity<Map<String, Object>>> getStationCount() {
        log.info("Getting station count from Redis Hash");

        return redisVersionedService.getStationsCount()
                .map(count -> ResponseEntity.ok(Map.of(
                        "count", count,
                        "hashKey", "public:stations:state",
                        "timestamp", Instant.now().toString()
                )));
    }

    /**
     * Получить все станции (устаревший метод, оставлен для обратной совместимости)
     * GET /api/cached-stations/legacy
     */
    @GetMapping("/legacy")
    @Deprecated
    public Flux<StationStateDTO> getAllStationsLegacy() {
        log.info("Getting all stations using legacy SCAN method");

        // Используем старый метод через SCAN
        return redisVersionedService.getAllStationsAsFlux();
    }

    /**
     * Удалить станцию
     * DELETE /api/cached-stations/{stationId}
     */
    @DeleteMapping("/{stationId}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteStation(@PathVariable String stationId) {
        log.info("Deleting station from Redis: {}", stationId);

        return redisVersionedService.deleteStationState(stationId)
                .map(deleted -> ResponseEntity.ok(Map.of(
                        "stationId", stationId,
                        "deleted", deleted,
                        "timestamp", Instant.now().toString()
                )));
    }

    /**
     * Перестроить Hash (принудительная перезагрузка)
     * POST /api/cached-stations/rebuild-hash
     */
    @PostMapping("/rebuild-hash")
    public Mono<ResponseEntity<Map<String, Object>>> rebuildHash() {
        log.warn("Manual hash rebuild requested");

        // Здесь нужно вызвать метод перестройки Hash
        // Для простоты возвращаем информационное сообщение

        return Mono.just(ResponseEntity.ok(Map.of(
                "message", "Use /api/state-updater/reload to rebuild stations hash",
                "timestamp", Instant.now().toString()
        )));
    }
}