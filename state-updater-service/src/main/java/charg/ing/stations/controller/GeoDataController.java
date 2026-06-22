package charg.ing.stations.controller;

import charg.ing.stations.dto.StationGeoData;
import charg.ing.stations.service.GeoDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
@Slf4j
public class GeoDataController {

    private final GeoDataService geoDataService;

    @GetMapping("/station/{stationId}")
    public Mono<ResponseEntity<StationGeoData>> getStationGeoData(@PathVariable String stationId) {
        return geoDataService.getStationGeoData(stationId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/radius")
    public Flux<StationGeoData> findStationsInRadius(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(defaultValue = "5.0") double radiusKm) {

        log.info("Searching stations within {}km of ({}, {})", radiusKm, latitude, longitude);
        return geoDataService.findStationsInRadius(longitude, latitude, radiusKm);
    }

    @GetMapping("/bounding-box")
    public Flux<StationGeoData> findStationsInBoundingBox(
            @RequestParam double minLon,
            @RequestParam double minLat,
            @RequestParam double maxLon,
            @RequestParam double maxLat) {

        log.info("Searching stations in bounding box: ({},{}) to ({},{})",
                minLon, minLat, maxLon, maxLat);
        return geoDataService.findStationsInBoundingBox(minLon, minLat, maxLon, maxLat);
    }

    @GetMapping("/distance")
    public Mono<ResponseEntity<Map<String, Object>>> getDistance(
            @RequestParam String stationId1,
            @RequestParam String stationId2) {

        return geoDataService.getDistanceBetweenStations(stationId1, stationId2)
                .map(distance -> {
                    // 1. Явно создаем карту нужного типа
                    Map<String, Object> response = new HashMap<>();

                    // 2. Наполняем ее данными
                    response.put("stationId1", stationId1);
                    response.put("stationId2", stationId2);
                    response.put("distanceMeters", distance);

                    // 3. ВАЖНО: Используйте 1000.0 для корректного деления с плавающей точкой
                    response.put("distanceKm", distance / 1000.0);

                    return response;
                })
//                .map(distance -> Map.of(
//                        "stationId1", stationId1,
//                        "stationId2", stationId2,
//                        "distanceMeters", distance,
//                        "distanceKm", distance / 1000
//                ))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getGeoStats() {
        return geoDataService.getGeoIndexSize()
                .map(size -> {
                    // 1. Явно создаем карту нужного типа
                    Map<String, Object> response = new HashMap<>();

                    // 2. Наполняем ее
                    response.put("stationsInGeoIndex", size);
                    response.put("database", 1);
                    response.put("geoIndexKey", "stations:geo:index");

                    return response;
                })
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/clear")
    public Mono<ResponseEntity<String>> clearAllGeoData() {
        return geoDataService.clearAllGeoData()
                .map(count -> ResponseEntity.ok("Cleared " + count + " geo data records"))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.status(500).body("Error clearing geo data: " + e.getMessage())));
    }
}
