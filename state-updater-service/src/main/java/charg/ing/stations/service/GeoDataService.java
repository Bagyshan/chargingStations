package charg.ing.stations.service;

import charg.ing.stations.dto.StationDTO;
import charg.ing.stations.dto.StationGeoData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.domain.geo.*;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
//@RequiredArgsConstructor
@Slf4j
public class GeoDataService {

    private static final String GEO_INDEX_KEY = "stations:geo:index";
    private static final String GEO_DATA_PREFIX = "station:geo:";
    private static final String ALL_STATIONS_GEO_KEY = "stations:geo:all";

    @Qualifier("geoStringRedisTemplate")
    private final ReactiveStringRedisTemplate geoRedisTemplate;

    @Qualifier("geoRedisTemplate")
    private final ReactiveRedisTemplate<String, String> geoDataRedisTemplate;

    private final ObjectMapper objectMapper;

    // Пишем конструктор вручную
    public GeoDataService(
            @Qualifier("geoStringRedisTemplate") ReactiveStringRedisTemplate geoRedisTemplate,
            @Qualifier("geoRedisTemplate") ReactiveRedisTemplate<String, String> geoDataRedisTemplate,
            ObjectMapper objectMapper) {
        this.geoRedisTemplate = geoRedisTemplate;
        this.geoDataRedisTemplate = geoDataRedisTemplate;
        this.objectMapper = objectMapper;
    }


    public Flux<GeoResult<RedisGeoCommands.GeoLocation<String>>> findAllStationsWithDistance(double longitude, double latitude) {
        double radiusKm = 20000; // достаточно для покрытия всей Земли
        Circle circle = new Circle(new Point(longitude, latitude), new Distance(radiusKm, Metrics.KILOMETERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()
                .sortAscending();
        return geoRedisTemplate.opsForGeo()
                .radius(GEO_INDEX_KEY, circle, args)
                .onErrorResume(e -> {
                    log.error("Error finding stations with distance: {}", e.getMessage());
                    return Flux.empty();
                });
    }



    /**
     * Сохраняет геоданные станции из StationDTO
     */
    public Mono<Boolean> saveStationGeoData(StationDTO stationDTO) {
        if (stationDTO.getGeolocation() == null ||
                stationDTO.getGeolocation().getLatitude() == null ||
                stationDTO.getGeolocation().getLongitude() == null) {
            return Mono.just(false);
        }

        StationGeoData geoData = StationGeoData.fromStationDTO(stationDTO);
        return saveStationGeoData(geoData);
    }

    /**
     * Сохраняет геоданные станции из StationGeoData
     */
    public Mono<Boolean> saveStationGeoData(StationGeoData geoData) {
        if (!geoData.hasValidCoordinates()) {
            log.debug("Station {} has invalid coordinates, skipping geo data",
                    geoData.getChargeBoxId());
            return Mono.just(false);
        }

        String geoDataKey = GEO_DATA_PREFIX + geoData.getChargeBoxId();

        // 1. Добавляем в GEO индекс
        Mono<Long> addToGeoIndex = geoRedisTemplate.opsForGeo()
                .add(GEO_INDEX_KEY,
                        // 1. Точка (координаты)
                        new org.springframework.data.geo.Point(geoData.getLongitude(), geoData.getLatitude()),
                        // 2. Идентификатор члена (ID станции)
                        geoData.getChargeBoxId())
//                .add(GEO_INDEX_KEY,
//                        new GeoLocation<>(geoData.getChargeBoxId(),
//                                new org.springframework.data.geo.Point(geoData.getLongitude(), geoData.getLatitude())))
                .doOnNext(count -> log.debug("Added station {} to GEO index", geoData.getChargeBoxId()));

        // 2. Сохраняем дополнительные данные как Hash
        Map<String, String> geoHashData = new HashMap<>();
        geoHashData.put("chargeBoxId", geoData.getChargeBoxId());
        geoHashData.put("id", String.valueOf(geoData.getId()));
        geoHashData.put("coordinates", geoData.getCoordinates() != null ? geoData.getCoordinates() : "");
        geoHashData.put("latitude", String.valueOf(geoData.getLatitude()));
        geoHashData.put("longitude", String.valueOf(geoData.getLongitude()));
        geoHashData.put("version", String.valueOf(geoData.getVersion()));

        Mono<Boolean> saveGeoHash = geoDataRedisTemplate.opsForHash()
                .putAll(geoDataKey, geoHashData)
//                .map(count -> count > 0)
                .doOnNext(success -> {
                    if (success) {
                        log.debug("Saved geo hash for station {}", geoData.getChargeBoxId());
                    }
                });

        return Mono.zip(addToGeoIndex, saveGeoHash)
                .map(tuple -> tuple.getT1() > 0 && tuple.getT2())
                .doOnSuccess(success -> {
                    if (success) {
                        log.debug("Successfully saved geo data for station {}",
                                geoData.getChargeBoxId());
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error saving geo data for station {}: {}",
                            geoData.getChargeBoxId(), error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Удаляет геоданные станции
     */
    public Mono<Boolean> removeStationGeoData(String stationId) {
        String geoDataKey = GEO_DATA_PREFIX + stationId;

        // 1. Удаляем из GEO индекса
        Mono<Long> removeFromGeoIndex = geoRedisTemplate.opsForGeo()
                .remove(GEO_INDEX_KEY, stationId);

        // 2. Удаляем Hash данные
        Mono<Boolean> deleteGeoHash = geoDataRedisTemplate.delete(geoDataKey)
                .map(count -> count > 0);

        // 3. Удаляем JSON
        Mono<Boolean> deleteJson = geoDataRedisTemplate.delete(geoDataKey + ":json")
                .map(count -> count > 0);

        return Mono.zip(removeFromGeoIndex, deleteGeoHash, deleteJson)
                .map(tuple -> tuple.getT1() > 0 || tuple.getT2() || tuple.getT3())
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Removed geo data for station {}", stationId);
                    }
                });
    }

    /**
     * Получает геоданные станции
     */
    public Mono<StationGeoData> getStationGeoData(String stationId) {
        String geoDataKey = GEO_DATA_PREFIX + stationId;

        return geoDataRedisTemplate.opsForHash()
                .entries(geoDataKey)
                .collectMap(entry -> (String) entry.getKey(), entry -> (String) entry.getValue())
                .flatMap(hashData -> {
                    if (hashData.isEmpty()) {
                        return Mono.empty();
                    }

                    try {
                        StationGeoData geoData = new StationGeoData();
                        geoData.setChargeBoxId(hashData.get("chargeBoxId"));
                        geoData.setId(Integer.parseInt(hashData.get("id")));
                        geoData.setCoordinates(hashData.get("coordinates"));

                        if (hashData.get("latitude") != null) {
                            geoData.setLatitude(Double.parseDouble(hashData.get("latitude")));
                        }
                        if (hashData.get("longitude") != null) {
                            geoData.setLongitude(Double.parseDouble(hashData.get("longitude")));
                        }
                        if (hashData.get("version") != null) {
                            geoData.setVersion(Integer.parseInt(hashData.get("version")));
                        }

                        return Mono.just(geoData);
                    } catch (Exception e) {
                        log.error("Failed to parse geo data for station {}: {}", stationId, e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    /**
     * Ищет станции в радиусе (в километрах)
     */
    public Flux<StationGeoData> findStationsInRadius(double longitude, double latitude, double radiusKm) {
        return geoRedisTemplate.opsForGeo()
                .radius(GEO_INDEX_KEY,
                        new org.springframework.data.geo.Circle(
                                new org.springframework.data.geo.Point(longitude, latitude),
                                radiusKm * 1000), // Конвертируем км в метры
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeCoordinates()
                                .includeDistance()
                                .sortAscending())
                .flatMap(geoResult -> {
                    String stationId = geoResult.getContent().getName();
                    return getStationGeoData(stationId)
                            .map(geoData -> {
                                // Можно добавить расстояние к результату
                                return geoData;
                            })
                            .onErrorResume(e -> {
                                log.warn("Failed to get geo data for station {}: {}", stationId, e.getMessage());
                                return Mono.empty();
                            });
                });
    }

    /**
     * Ищет станции в bounding box
     */
    public Flux<StationGeoData> findStationsInBoundingBox(
            double minLongitude, double minLatitude,
            double maxLongitude, double maxLatitude) {


        // 1. Создаем объект Box (фигура для поиска)
        Shape boundingBox = new Box(
                new Point(minLongitude, minLatitude),
                new Point(maxLongitude, maxLatitude)
        );

        GeoReference<String> referencePoint = GeoReference.fromCoordinate(minLongitude, minLatitude);

        // 2. Создаем аргументы для результата (какие данные возвращать)
        RedisGeoCommands.GeoSearchCommandArgs searchArgs = RedisGeoCommands.GeoSearchCommandArgs
                .newGeoSearchArgs()
                .includeCoordinates();

        return geoRedisTemplate.opsForGeo()
                .search(GEO_INDEX_KEY, referencePoint, (GeoShape) boundingBox, searchArgs)
//                .search(GEO_INDEX_KEY,
//                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
//                                .within(new org.springframework.data.geo.Box(
//                                        new org.springframework.data.geo.Point(minLongitude, minLatitude),
//                                        new org.springframework.data.geo.Point(maxLongitude, maxLatitude)))
//                                .includeCoordinates())
                .flatMap(geoResult -> getStationGeoData(String.valueOf(geoResult.getContent())))
                .onErrorResume(e -> {
                    log.error("Error searching stations in bounding box: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Получает расстояние между двумя станциями (в метрах)
     */
    public Mono<Double> getDistanceBetweenStations(String stationId1, String stationId2) {
        return geoRedisTemplate.opsForGeo()
                .distance(GEO_INDEX_KEY, stationId1, stationId2, Metrics.METERS)
                .map(Distance::getValue)
                .map(distance -> Double.parseDouble(String.valueOf(distance)))
                .doOnError(e -> log.error("Error calculating distance: {}", e.getMessage()));
    }

    /**
     * Очищает все геоданные
     */
    public Mono<Long> clearAllGeoData() {
        // Находим все ключи геоданных
        return geoDataRedisTemplate.keys(GEO_DATA_PREFIX + "*")
                .concatWith(Flux.just(GEO_INDEX_KEY, ALL_STATIONS_GEO_KEY))
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(0L);
                    }
                    return geoDataRedisTemplate.delete(keys.toArray(new String[0]));
                })
                .doOnSuccess(count -> log.info("Cleared {} geo data keys", count));
    }

    /**
     * Получает количество станций в GEO индексе
     */
    public Mono<Long> getGeoIndexSize() {
        return geoRedisTemplate.opsForZSet()
                .size(GEO_INDEX_KEY)
                .defaultIfEmpty(0L);
    }
}