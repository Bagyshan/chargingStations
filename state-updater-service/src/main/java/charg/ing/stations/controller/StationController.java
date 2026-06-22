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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            @RequestParam(required = false, defaultValue = "false") boolean availableOnly) {

        // Проверка на частичную передачу параметров
        if (longitude == null && latitude == null) {
            // Режим без расстояния
            log.info("Getting ALL stations from Redis Hash (without distance), availableOnly={}", availableOnly);
            return redisVersionedService.getAllStationsFromHash()
                    .flatMap(stations -> {
                        // Преобразуем Map<String, StationStateDTO> в List<StationStateWithDistance>
                        List<StationStateWithDistance> stationList = stations.values().stream()
                                .filter(dto -> !availableOnly || isAvailable(dto))
                                .map(dto -> new StationStateWithDistance(dto, null)) // distance = null
                                .toList();
                        return Mono.just(AllStationsResponse.of(stationList));
                    })
                    .map(ResponseEntity::ok);
        } else if (longitude != null && latitude != null) {
            // Режим с расстоянием
            log.info("Getting ALL stations with distance from point ({}, {}), availableOnly={}", longitude, latitude, availableOnly);
            return getAllStationsWithDistance(longitude, latitude, availableOnly)
                    .map(ResponseEntity::ok);
        } else {
            // Передан только один параметр — ошибка. Возвращаем 400 Bad Request.
            return Mono.just(ResponseEntity.badRequest().body(
                    AllStationsResponse.of("Both longitude and latitude must be provided together")
            ));
        }
    }
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

    private Mono<AllStationsResponse> getAllStationsWithDistance(double longitude, double latitude, boolean availableOnly) {
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
                        StationStateDTO stationData = stations.get(stationId);
                        if (stationData != null) {
                            if (availableOnly && !isAvailable(stationData)) {
                                continue; // фильтр: только доступные станции
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