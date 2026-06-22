package charg.ing.stations.service.util;

import charg.ing.stations.dto.*;
import charg.ing.stations.service.DataInitializationService;
import charg.ing.stations.service.GeoDataService;
import charg.ing.stations.service.KafkaConsumerLifecycleManager;
import charg.ing.stations.service.RedisStreamService;
import charg.ing.stations.service.RedisVersionedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaMessageProcessor {

    private final RedisVersionedService redisVersionedService;
    private final GeoDataService geoDataService;
    private final RedisStreamService redisStreamService;
    private final KafkaConsumerLifecycleManager kafkaConsumerLifecycleManager;
//    private final DataInitializationService initializationService;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @EventListener
    public void onInitializationCompleted(DataInitializationCompletedEvent event) {
        kafkaConsumerLifecycleManager.start();
        log.info("Data initialization completed event received. Enabling Kafka processing.");
        setEnabled(true);
    }

    /**
     * Обрабатывает сообщение из Kafka
     */
    public Mono<Boolean> processMessage(StationStateDTO stationState) {
        if (!enabled.get()) {
            log.debug(
                    "Kafka processing is disabled, skipping message for station: {}",
                    stationState.getStationId()
            );
            return Mono.just(false);
        }

//        if (!initializationService.isInitialized()) {
//            log.debug(
//                    "Data initialization not completed, skipping Kafka message for station: {}",
//                    stationState.getStationId()
//            );
//            return Mono.just(false);
//        }

        log.debug("Processing Kafka message for station: {}, version: {}",
                stationState.getStationId(),
                stationState.getVersion());

        isProcessing.set(true);

        // Получаем текущее состояние станции для сравнения
        return redisVersionedService.getStationState(stationState.getStationId())
                .flatMap(existingState -> {
                    // Станция существует, проверяем нужно ли обновлять
                    return handleExistingStation(stationState, existingState);
                })
                .switchIfEmpty(
                        // Станция не существует, создаем новую
                        handleNewStation(stationState)
                )
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Successfully processed message for station {}",
                                stationState.getStationId());
                    } else {
                        log.debug("No updates needed for station {}",
                                stationState.getStationId());
                    }
                })
                .doFinally(signal -> isProcessing.set(false))
                .onErrorResume(error -> {
                    log.error(
                            "Error processing Kafka message for station {}: {}",
                            stationState.getStationId(),
                            error.getMessage(),
                            error
                    );
                    return Mono.just(false);
                });
    }

    /**
     * Обрабатывает обновление существующей станции
     */
    private Mono<Boolean> handleExistingStation(StationStateDTO newState, StationStateDTO existingState) {
        // Проверяем, является ли новая версия более новой
        if (newState.getVersion() > existingState.getVersion()) {
            log.debug("Updating station {} from version {} to {}",
                    newState.getStationId(),
                    existingState.getVersion(),
                    newState.getVersion());

            // 1. Проверяем изменения в коннекторах для специальных событий
            Mono<Void> connectorEvents = checkConnectorChanges(newState, existingState);

            // 2. Обновляем данные в Redis
            Mono<Boolean> updateData = updateStationData(newState, existingState.getVersion());

            // 3. Обновляем геоданные если они изменились
            Mono<Boolean> updateGeoData = updateGeoDataIfChanged(newState, existingState);

            // Используем flatMap вместо zip, чтобы избежать null значений
            return connectorEvents
                    .then(Mono.zip(
                            updateData.defaultIfEmpty(false),
                            updateGeoData.defaultIfEmpty(false)
                    ))
                    .map(tuple -> {
                        Boolean dataUpdated = tuple.getT1();
                        Boolean geoUpdated = tuple.getT2();

                        // Если хотя бы одно обновление прошло успешно
                        return Boolean.TRUE.equals(dataUpdated) || Boolean.TRUE.equals(geoUpdated);
                    })
                    .defaultIfEmpty(false)
                    .doOnSuccess(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            log.info("Station {} updated to version {}",
                                    newState.getStationId(),
                                    newState.getVersion());
                        } else {
                            log.debug("Station {} update completed but no changes were made",
                                    newState.getStationId());
                        }
                    });

        } else {
            log.debug("Skipping update for station {}, version {} is not newer than existing {}",
                    newState.getStationId(),
                    newState.getVersion(),
                    existingState.getVersion());
            return Mono.just(false);
        }
    }

    /**
     * Создает новую станцию
     */
    private Mono<Boolean> handleNewStation(StationStateDTO stationState) {
        log.info("Creating new station: {}, version: {}",
                stationState.getStationId(),
                stationState.getVersion());

        // 1. Сохраняем основные данные
        Mono<Boolean> saveMainData = redisVersionedService.updateStationStateIfNewer(stationState);

        // 2. Сохраняем геоданные (если есть)
        Mono<Boolean> saveGeoData = saveStationGeoData(stationState);

        // Обрабатываем оба результата
        return Mono.zip(
                        saveMainData.defaultIfEmpty(false),
                        saveGeoData.defaultIfEmpty(false)
                )
                .map(tuple -> {
                    Boolean mainSaved = tuple.getT1();
                    Boolean geoSaved = tuple.getT2();

                    // Если хотя бы одна операция прошла успешно
                    return Boolean.TRUE.equals(mainSaved) || Boolean.TRUE.equals(geoSaved);
                })
                .defaultIfEmpty(false)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("New station {} created with version {}",
                                stationState.getStationId(),
                                stationState.getVersion());
                    } else {
                        log.warn("Failed to create station {}",
                                stationState.getStationId());
                    }
                });
    }

    /**
     * Проверяет изменения в коннекторах и публикует специальные события
     */
    private Mono<Void> checkConnectorChanges(StationStateDTO newState, StationStateDTO oldState) {
        return Mono.fromRunnable(() -> {
            if (newState.getConnectors() != null && oldState.getConnectors() != null) {
                // Создаем Map существующих коннекторов для быстрого поиска
                var oldConnectorsMap = oldState.getConnectors().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                StationStateDTO.ConnectorState::getConnectorId,
                                connector -> connector
                        ));

                // Проверяем каждый новый коннектор
                newState.getConnectors().forEach(newConnector -> {
                    StationStateDTO.ConnectorState oldConnector = oldConnectorsMap.get(newConnector.getConnectorId());

                    if (oldConnector != null) {
                        // Коннектор существует, проверяем изменения статуса
                        if (!newConnector.getStatus().equals(oldConnector.getStatus())) {
                            // Статус изменился, публикуем событие
                            StationEventDTO event = StationEventDTO.createConnectorStatusChangedEvent(
                                    newState.getStationId(),
                                    newConnector.getConnectorId(),
                                    oldConnector.getStatus(),
                                    newConnector.getStatus(),
                                    newState.getVersion()
                            );

                            redisStreamService.publishEvent(event)
                                    .doOnSuccess(recordId -> log.debug(
                                            "Published connector status change event for station {} connector {}: {} -> {}",
                                            newState.getStationId(),
                                            newConnector.getConnectorId(),
                                            oldConnector.getStatus(),
                                            newConnector.getStatus()
                                    ))
                                    .subscribe();
                        }
                    } else {
                        // Новый коннектор
                        log.debug("New connector {} added to station {}",
                                newConnector.getConnectorId(),
                                newState.getStationId());
                    }
                });

                // Проверяем удаленные коннекторы
                oldState.getConnectors().forEach(oldConnector -> {
                    boolean stillExists = newState.getConnectors().stream()
                            .anyMatch(c -> c.getConnectorId().equals(oldConnector.getConnectorId()));

                    if (!stillExists) {
                        log.debug("Connector {} removed from station {}",
                                oldConnector.getConnectorId(),
                                newState.getStationId());
                    }
                });
            }
        });
    }

    /**
     * Обновляет данные станции
     */
    private Mono<Boolean> updateStationData(StationStateDTO stationState, Integer oldVersion) {
        // Обновление через RedisVersionedService уже включает публикацию события
        return redisVersionedService.updateStationStateIfNewer(stationState)
                .defaultIfEmpty(false)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Station data updated for {}", stationState.getStationId());
                    }
                });
    }

    /**
     * Сохраняет/обновляет геоданные станции
     */
    private Mono<Boolean> saveStationGeoData(StationStateDTO stationState) {
        // Проверяем есть ли геоданные
        if (stationState.getGeolocation() == null ||
                stationState.getGeolocation().getLatitude() == null ||
                stationState.getGeolocation().getLongitude() == null) {
            return Mono.just(false);
        }

        // Создаем StationGeoData из StationStateDTO
        StationGeoData geoData = new StationGeoData();
        geoData.setChargeBoxId(stationState.getStationId());
        geoData.setId(stationState.getId());
        geoData.setVersion(stationState.getVersion());
        geoData.setLatitude(stationState.getGeolocation().getLatitude());
        geoData.setLongitude(stationState.getGeolocation().getLongitude());
        geoData.setCoordinates(stationState.getGeolocation().getCoordinates());

        // Сохраняем через GeoDataService
        return geoDataService.saveStationGeoData(geoData)
                .defaultIfEmpty(false)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Geo data saved for station {}", stationState.getStationId());
                    } else {
                        log.debug("Failed to save geo data for station {}",
                                stationState.getStationId());
                    }
                });
    }

    /**
     * Обновляет геоданные если они изменились
     */
    private Mono<Boolean> updateGeoDataIfChanged(StationStateDTO newState, StationStateDTO oldState) {
        // Проверяем, изменились ли геоданные
        boolean geoDataChanged = false;

        if (newState.getGeolocation() != null && oldState.getGeolocation() != null) {
            // Сравниваем координаты
            boolean latChanged = newState.getGeolocation().getLatitude() != null &&
                    oldState.getGeolocation().getLatitude() != null &&
                    !newState.getGeolocation().getLatitude().equals(oldState.getGeolocation().getLatitude());

            boolean lonChanged = newState.getGeolocation().getLongitude() != null &&
                    oldState.getGeolocation().getLongitude() != null &&
                    !newState.getGeolocation().getLongitude().equals(oldState.getGeolocation().getLongitude());

            geoDataChanged = latChanged || lonChanged;
        } else if (newState.getGeolocation() != null && oldState.getGeolocation() == null) {
            // Добавились геоданные
            geoDataChanged = true;
        } else if (newState.getGeolocation() == null && oldState.getGeolocation() != null) {
            // Удалились геоданные
            geoDataChanged = true;
        }

        if (geoDataChanged) {
            log.debug("Geo data changed for station {}, updating...", newState.getStationId());
            return saveStationGeoData(newState);
        } else {
            return Mono.just(false);
        }
    }

    /**
     * Включает/выключает обработку Kafka сообщений
     */
    public void setEnabled(boolean enabled) {
        boolean oldValue = this.enabled.getAndSet(enabled);
        log.info("Kafka message processing {}", enabled ? "enabled" : "disabled");

        if (enabled && !oldValue) {
            log.info("Starting to process Kafka messages...");
        } else if (!enabled && oldValue) {
            log.info("Stopping Kafka message processing...");
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public boolean isProcessing() {
        return isProcessing.get();
    }
}



//package charg.ing.stations.service.util;
//
//import charg.ing.stations.dto.StationDTO;
//import charg.ing.stations.dto.StationEventDTO;
//import charg.ing.stations.dto.StationGeoData;
//import charg.ing.stations.dto.StationStateDTO;
//import charg.ing.stations.service.*;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.event.EventListener;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//
//import java.util.concurrent.atomic.AtomicBoolean;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class KafkaMessageProcessor {
//
//    private final RedisVersionedService redisVersionedService;
////    private final DataInitializationService initializationService;
//    private final KafkaConsumerLifecycleManager kafkaConsumerLifecycleManager;
//    private final RedisStreamService redisStreamService;
//    private final GeoDataService geoDataService;
//
//    private final AtomicBoolean enabled = new AtomicBoolean(false);
//    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
//
//    @EventListener
//    public void onInitializationCompleted(DataInitializationCompletedEvent event) {
//        kafkaConsumerLifecycleManager.start();
//        log.info("Data initialization completed event received. Enabling Kafka processing.");
//        setEnabled(true);
//    }
//
////    /**
////     * Обрабатывает сообщение из Kafka
////     */
////    public Mono<Boolean> processMessage(StationStateDTO stationState) {
////        if (!enabled.get()) {
////            log.debug("Kafka processing is disabled, skipping message for station: {}",
////                    stationState.getStationId());
////            return Mono.just(false);
////        }
////
////        if (!initializationService.isInitialized()) {
////            log.debug("Initialization not completed, skipping Kafka message for station: {}",
////                    stationState.getStationId());
////            return Mono.just(false);
////        }
////
////        isProcessing.set(true);
////        return redisVersionedService.updateStationStateIfNewer(stationState)
////                .doOnNext(success -> {
////                    if (success) {
////                        log.debug("Processed Kafka message for station {} version {}",
////                                stationState.getStationId(),
////                                stationState.getVersion());
////                    }
////                })
////                .doFinally(signal -> isProcessing.set(false))
////                .onErrorResume(error -> {
////                    log.error("Error processing Kafka message for station {}: {}",
////                            stationState.getStationId(),
////                            error.getMessage());
////                    return Mono.just(false);
////                });
////    }
//
//
//    /**
//     * Обрабатывает сообщение из Kafka
//     */
//    public Mono<Boolean> processMessage(StationStateDTO stationState) {
//        if (!enabled.get()) {
//            log.debug(
//                    "Kafka processing is disabled, skipping message for station: {}",
//                    stationState.getStationId()
//            );
//            return Mono.just(false);
//        }
//
////        if (!initializationService.isInitialized()) {
////            log.debug(
////                    "Data initialization not completed, skipping Kafka message for station: {}",
////                    stationState.getStationId()
////            );
////            return Mono.just(false);
////        }
//
//        log.debug("Processing Kafka message for station: {}, version: {}",
//                stationState.getStationId(),
//                stationState.getVersion());
//
//
//        log.error("ENABLED: {}", enabled.get());
//
//        isProcessing.set(true);
//
//        log.error("PROCESSING: {}", isProcessing.get());
//
//        // Получаем текущее состояние станции для сравнения
//        return redisVersionedService.getStationState(stationState.getStationId())
//                .flatMap(existingState -> {
//                    // Станция существует, проверяем нужно ли обновлять
//                    return handleExistingStation(stationState, existingState);
//                })
//                .switchIfEmpty(
//                        // Станция не существует, создаем новую
//                        handleNewStation(stationState)
//                )
//                .doOnSuccess(success -> {
//                    if (success) {
//                        log.debug("Successfully processed message for station {}",
//                                stationState.getStationId());
//                    }
//                })
//                .doFinally(signal -> isProcessing.set(false))
//                .onErrorResume(error -> {
//                    log.error(
//                            "Error processing Kafka message for station {}: {}",
//                            stationState.getStationId(),
//                            error.getMessage(),
//                            error
//                    );
//                    return Mono.just(false);
//                });
//    }
//
////    /**
////     * Обрабатывает сообщение из Kafka
////     */
////    public Mono<Boolean> processMessage(StationStateDTO stationState) {
////        if (!enabled.get()) {
////            log.debug(
////                    "Kafka processing is disabled, skipping message for station: {}",
////                    stationState.getStationId()
////            );
////            return Mono.just(false);
////        }
////
////        log.error("ENABLED: {}", enabled.get());
////
////        isProcessing.set(true);
////
////        log.error("PROCESSING: {}", isProcessing.get());
////
////        return redisVersionedService.updateStationStateIfNewer(stationState)
////                .doOnNext(success -> {
////                    if (success) {
////                        log.debug(
////                                "Processed Kafka message for station {} version {}",
////                                stationState.getStationId(),
////                                stationState.getVersion()
////                        );
////                    }
////                })
////                .doFinally(signal -> isProcessing.set(false))
////                .onErrorResume(error -> {
////                    log.error(
////                            "Error processing Kafka message for station {}: {}",
////                            stationState.getStationId(),
////                            error.getMessage(),
////                            error
////                    );
////                    return Mono.just(false);
////                });
////    }
//
//
//    /**
//     * Обрабатывает обновление существующей станции
//     */
//    private Mono<Boolean> handleExistingStation(StationStateDTO newState, StationStateDTO existingState) {
//        // Проверяем, является ли новая версия более новой
//        if (newState.getVersion() > existingState.getVersion()) {
//            log.debug("Updating station {} from version {} to {}",
//                    newState.getStationId(),
//                    existingState.getVersion(),
//                    newState.getVersion());
//
//            // 1. Проверяем изменения в коннекторах для специальных событий
//            Mono<Void> connectorEvents = checkConnectorChanges(newState, existingState);
//
//            // 2. Обновляем данные в Redis
//            Mono<Boolean> updateData = updateStationData(newState, existingState.getVersion());
//
//            // 3. Обновляем геоданные если они изменились
//            Mono<Boolean> updateGeoData = updateGeoDataIfChanged(newState, existingState);
//
//            return Mono.zip(connectorEvents, updateData, updateGeoData)
//                    .map(tuple -> tuple.getT2() || tuple.getT3()) // Считаем успешным если обновились данные или гео
//                    .doOnSuccess(success -> {
//                        if (success) {
//                            log.info("Station {} updated to version {}",
//                                    newState.getStationId(),
//                                    newState.getVersion());
//                        }
//                    });
//
//        } else {
//            log.debug("Skipping update for station {}, version {} is not newer than existing {}",
//                    newState.getStationId(),
//                    newState.getVersion(),
//                    existingState.getVersion());
//            return Mono.just(false);
//        }
//    }
//
//    /**
//     * Создает новую станцию
//     */
//    private Mono<Boolean> handleNewStation(StationStateDTO stationState) {
//        log.info("Creating new station: {}, version: {}",
//                stationState.getStationId(),
//                stationState.getVersion());
//
//        // 1. Сохраняем основные данные
//        Mono<Boolean> saveMainData = redisVersionedService.updateStationStateIfNewer(stationState);
//
//        // 2. Сохраняем геоданные (если есть)
//        Mono<Boolean> saveGeoData = saveStationGeoData(stationState);
//
//        // 3. Публикуем событие создания (через RedisVersionedService уже должно быть опубликовано)
//
//        return Mono.zip(saveMainData, saveGeoData)
//                .map(tuple -> tuple.getT1() || tuple.getT2())
//                .doOnSuccess(success -> {
//                    if (success) {
//                        log.info("New station {} created with version {}",
//                                stationState.getStationId(),
//                                stationState.getVersion());
//                    }
//                });
//    }
//
//    /**
//     * Проверяет изменения в коннекторах и публикует специальные события
//     */
//    private Mono<Void> checkConnectorChanges(StationStateDTO newState, StationStateDTO oldState) {
//        return Mono.fromRunnable(() -> {
//            if (newState.getConnectors() != null && oldState.getConnectors() != null) {
//                // Создаем Map существующих коннекторов для быстрого поиска
//                var oldConnectorsMap = oldState.getConnectors().stream()
//                        .collect(java.util.stream.Collectors.toMap(
//                                StationStateDTO.ConnectorState::getConnectorId,
//                                connector -> connector
//                        ));
//
//                // Проверяем каждый новый коннектор
//                newState.getConnectors().forEach(newConnector -> {
//                    StationStateDTO.ConnectorState oldConnector = oldConnectorsMap.get(newConnector.getConnectorId());
//
//                    if (oldConnector != null) {
//                        // Коннектор существует, проверяем изменения статуса
//                        if (!newConnector.getStatus().equals(oldConnector.getStatus())) {
//                            // Статус изменился, публикуем событие
//                            StationEventDTO event = StationEventDTO.createConnectorStatusChangedEvent(
//                                    newState.getStationId(),
//                                    newConnector.getConnectorId(),
//                                    oldConnector.getStatus(),
//                                    newConnector.getStatus(),
//                                    newState.getVersion()
//                            );
//
//                            redisStreamService.publishEvent(event)
//                                    .doOnSuccess(recordId -> log.debug(
//                                            "Published connector status change event for station {} connector {}: {} -> {}",
//                                            newState.getStationId(),
//                                            newConnector.getConnectorId(),
//                                            oldConnector.getStatus(),
//                                            newConnector.getStatus()
//                                    ))
//                                    .subscribe();
//                        }
//                    } else {
//                        // Новый коннектор
//                        log.debug("New connector {} added to station {}",
//                                newConnector.getConnectorId(),
//                                newState.getStationId());
//                    }
//                });
//
//                // Проверяем удаленные коннекторы
//                oldState.getConnectors().forEach(oldConnector -> {
//                    boolean stillExists = newState.getConnectors().stream()
//                            .anyMatch(c -> c.getConnectorId().equals(oldConnector.getConnectorId()));
//
//                    if (!stillExists) {
//                        log.debug("Connector {} removed from station {}",
//                                oldConnector.getConnectorId(),
//                                newState.getStationId());
//                    }
//                });
//            }
//        });
//    }
//
//    /**
//     * Обновляет данные станции
//     */
//    private Mono<Boolean> updateStationData(StationStateDTO stationState, Integer oldVersion) {
//        // Обновление через RedisVersionedService уже включает публикацию события
//        return redisVersionedService.updateStationStateIfNewer(stationState)
//                .doOnSuccess(success -> {
//                    if (success) {
//                        log.debug("Station data updated for {}", stationState.getStationId());
//                    }
//                });
//    }
//
//    /**
//     * Сохраняет/обновляет геоданные станции
//     */
//    private Mono<Boolean> saveStationGeoData(StationStateDTO stationState) {
//        // Создаем StationGeoData из StationStateDTO
//        StationGeoData geoData = new StationGeoData();
//        geoData.setChargeBoxId(stationState.getStationId());
//        geoData.setId(stationState.getId());
//        geoData.setVersion(stationState.getVersion());
//
//        if (stationState.getGeolocation() != null) {
//            geoData.setLatitude(stationState.getGeolocation().getLatitude());
//            geoData.setLongitude(stationState.getGeolocation().getLongitude());
//            geoData.setCoordinates(stationState.getGeolocation().getCoordinates());
//        }
//
//        // Сохраняем через GeoDataService
//        return geoDataService.saveStationGeoData(geoData)
//                .doOnSuccess(success -> {
//                    if (success && geoData.hasValidCoordinates()) {
//                        log.debug("Geo data saved for station {}", stationState.getStationId());
//                    } else if (!geoData.hasValidCoordinates()) {
//                        log.debug("Station {} has no valid coordinates, skipping geo data",
//                                stationState.getStationId());
//                    }
//                });
//    }
//
//    /**
//     * Обновляет геоданные если они изменились
//     */
//    private Mono<Boolean> updateGeoDataIfChanged(StationStateDTO newState, StationStateDTO oldState) {
//        // Проверяем, изменились ли геоданные
//        boolean geoDataChanged = false;
//
//        if (newState.getGeolocation() != null && oldState.getGeolocation() != null) {
//            // Сравниваем координаты
//            boolean latChanged = newState.getGeolocation().getLatitude() != null &&
//                    !newState.getGeolocation().getLatitude().equals(oldState.getGeolocation().getLatitude());
//            boolean lonChanged = newState.getGeolocation().getLongitude() != null &&
//                    !newState.getGeolocation().getLongitude().equals(oldState.getGeolocation().getLongitude());
//
//            geoDataChanged = latChanged || lonChanged;
//        } else if (newState.getGeolocation() != null && oldState.getGeolocation() == null) {
//            // Добавились геоданные
//            geoDataChanged = true;
//        } else if (newState.getGeolocation() == null && oldState.getGeolocation() != null) {
//            // Удалились геоданные
//            geoDataChanged = true;
//        }
//
//        if (geoDataChanged) {
//            log.debug("Geo data changed for station {}, updating...", newState.getStationId());
//            return saveStationGeoData(newState);
//        } else {
//            return Mono.just(false);
//        }
//    }
//
//    /**
//     * Включает/выключает обработку Kafka сообщений
//     */
//    public void setEnabled(boolean enabled) {
//        boolean oldValue = this.enabled.getAndSet(enabled);
//        log.info("Kafka message processing {}", enabled ? "enabled" : "disabled");
//
//        if (enabled && !oldValue) {
//            log.info("Starting to process Kafka messages...");
//        } else if (!enabled && oldValue) {
//            log.info("Stopping Kafka message processing...");
//        }
//    }
//
//    public boolean isEnabled() {
//        return enabled.get();
//    }
//
//    public boolean isProcessing() {
//        return isProcessing.get();
//    }
//}