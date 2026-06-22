package charg.ing.stations.service;


//import charg.ing.stations.client.StationServiceClient;
import charg.ing.stations.dto.StationDTO;
import charg.ing.stations.dto.StationStateDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

///**
// * Основной сервис State Updater
// *
// * Назначение:
// * 1. Слушает топик Kafka station.state
// * 2. Обновляет состояние станций в Redis
// * 3. Обеспечивает идемпотентность через версионность
// * 4. Инициализирует кэш при старте приложения
// */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateUpdaterService {
//
//    private final RedisTemplate<String, Object> redisTemplate;
////    private final ReactiveRedisTemplate<String, String> redisTemplate;
//    private final RedisVersionedService redisVersionedService;
//    private final ObjectMapper objectMapper;
//    @Qualifier(value = "charg.ing.stations.client.StationServiceClient")
//    private final StationServiceClient stationServiceClient;
//
//    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
//    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
//
//    /**
//     * Инициализация кэша при запуске приложения
//     */
//    public void initializeCache() {
//        if (isInitialized.get() || isInitializing.getAndSet(true)) {
//            log.debug("Cache initialization already in progress or completed");
//            return;
//        }
//
//        log.info("🚀 Starting Redis cache initialization from station-service...");
//
//        try {
//            // Очищаем Redis
//            clearRedisCache();
//
//            List<StationDTO> allStations = stationServiceClient.getAllStations();
//            log.info("📥 Retrieved {} stations from station-service", allStations.size());
//
//            // Конвертируем в StationStateDTO
//            List<StationStateDTO> stationStates = convertToStationStateDTOs(allStations);
//
//            // Пакетная инициализация
//            int successCount = redisVersionedService.initializeStationsBatch(stationStates);
//
//            log.info("🎉 Cache initialization completed: {} successful, {} failed",
//                    successCount, stationStates.size() - successCount);
//
//            // Проверка данных
//            verifyRedisData();
//
//            isInitialized.set(true);
//
//        } catch (Exception e) {
//            log.error("💥 Failed to initialize Redis cache from station-service", e);
//            isInitialized.set(false);
//        } finally {
//            isInitializing.set(false);
//        }
//    }
//
//    /**
//     * Обработчик сообщений из Kafka
//     */
//    @KafkaListener(
//            topics = "${app.kafka.topics.station-state}",
//            groupId = "state-updater-group",
//            containerFactory = "kafkaListenerContainerFactory"
//    )
//    public void onStationState(StationStateDTO stationState) {
//        try {
//            log.debug("Received station state update for station: {}", stationState.getStationId());
//
//            boolean updated = redisVersionedService.updateStationStateWithVersioning(stationState);
//
//            if (updated) {
//                log.debug("✅ Successfully updated station: {}", stationState.getStationId());
//            } else {
//                log.debug("⏭️ Skipped station (version): {}", stationState.getStationId());
//            }
//
//        } catch (Exception e) {
//            log.error("Failed to process station state message: {}", stationState, e);
//        }
//    }
//
//    /**
//     * Конвертация StationDTO в StationStateDTO
//     */
//    private List<StationStateDTO> convertToStationStateDTOs(List<StationDTO> stations) {
//        List<StationStateDTO> result = new ArrayList<>();
//
//        for (StationDTO station : stations) {
//            StationStateDTO stationState = new StationStateDTO();
//            stationState.setStationId(station.getId());
//            stationState.setStationStatus(station.getStationStatus());
//            stationState.setVersion(station.getVersion());
//            stationState.setLastUpdated(station.getLastUpdated());
//            stationState.setLat(station.getLat());
//            stationState.setLon(station.getLon());
//            stationState.setName(station.getName());
//
//            List<StationStateDTO.ConnectorDTO> connectors = new ArrayList<>();
//            for (StationDTO.ConnectorDTO sourceConnector : station.getConnectors()) {
//                StationStateDTO.ConnectorDTO targetConnector = new StationStateDTO.ConnectorDTO();
//                targetConnector.setConnectorId(sourceConnector.getConnectorId());
//                targetConnector.setStatus(sourceConnector.getStatus());
//                targetConnector.setVersion(sourceConnector.getVersion());
//                targetConnector.setLastUpdated(sourceConnector.getLastUpdated());
//                targetConnector.setSessionId(sourceConnector.getSessionId());
//                targetConnector.setMeterValue(sourceConnector.getMeterValue());
//                connectors.add(targetConnector);
//            }
//
//            stationState.setConnectors(connectors);
//            result.add(stationState);
//        }
//
//        return result;
//    }
//
//    /**
//     * Очистка Redis кэша
//     */
//    private void clearRedisCache() {
//        try {
//            log.info("🧹 Clearing Redis cache...");
//            Set<String> keys = redisTemplate.keys("*");
//            if (keys != null && !keys.isEmpty()) {
//                redisTemplate.delete(keys);
//                log.info("✅ Cleared {} keys from Redis", keys.size());
//            } else {
//                log.info("ℹ️ Redis is already empty");
//            }
//        } catch (Exception e) {
//            log.warn("⚠️ Failed to clear Redis cache: {}", e.getMessage());
//        }
//    }
//
//    /**
//     * Проверка данных в Redis
//     */
//    private void verifyRedisData() {
//        try {
//            log.info("🔍 VERIFYING REDIS DATA...");
//
//            Set<String> keys = redisTemplate.keys("*");
//            log.info("📊 Total keys in Redis: {}", keys != null ? keys.size() : 0);
//
//            if (keys != null) {
//                for (String key : keys) {
//                    if (key.startsWith("station:") || key.startsWith("connector:")) {
//                        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
//                        log.info("🔑 Key: {} -> {}", key, data);
//                    } else if (key.equals("stations:index")) {
//                        Set<Object> members = redisTemplate.opsForSet().members(key);
//                        log.info("📋 Index: {} -> {}", key, members);
//                    }
//                }
//            }
//
//            log.info("✅ REDIS VERIFICATION COMPLETED");
//        } catch (Exception e) {
//            log.error("❌ Redis verification failed: {}", e.getMessage());
//        }
//    }
//
//    // Остальные методы остаются без изменений
//    public boolean isInitialized() {
//        return isInitialized.get();
//    }
//
//    public boolean isInitializing() {
//        return isInitializing.get();
//    }
}