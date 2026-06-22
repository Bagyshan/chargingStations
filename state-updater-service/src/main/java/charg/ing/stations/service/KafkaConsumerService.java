package charg.ing.stations.service;

import charg.ing.stations.dto.MeterValueMessage;
import charg.ing.stations.dto.StationEventDTO;
import charg.ing.stations.dto.StationStateDTO;
import charg.ing.stations.service.util.KafkaMessageProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final KafkaMessageProcessor kafkaMessageProcessor;
    private final DataInitializationService initializationService;
    private final RedisStreamService redisStreamService;      // NEW
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "station.state",
            groupId = "state-updater-group",
            autoStartup = "${kafka.consumer.auto-startup:false}"
    )
    public void consumeStationState(StationStateDTO stationState) {

        log.error("PROOOOOOOOOBLEMMMMMM I HAVE A MESSAGE");
        if (!initializationService.isInitialized()) {
            log.debug("Initialization not completed, deferring Kafka message for station: {}",
                    stationState.getStationId());
            return;
        }

        log.debug("Received Kafka message for station: {}, version: {}",
                stationState.getStationId(),
                stationState.getVersion());

        kafkaMessageProcessor.processMessage(stationState)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        success -> {
                            if (success) {
                                log.trace("Successfully processed Kafka message for station: {}",
                                        stationState.getStationId());
                            }
                        },
                        error -> log.error("Failed to process Kafka message for {}: {}",
                                stationState.getStationId(),
                                error.getMessage())
                );
    }



    // ========== NEW: слушатель для топика station.meter.values ==========

    @KafkaListener(
            topics = "station.meter.values",
            groupId = "state-updater-group",
            containerFactory = "stringKafkaListenerContainerFactory",
            autoStartup = "${kafka.consumer.auto-startup:false}"
    )
    public void consumeMeterValue(String message) {
        if (!initializationService.isInitialized()) {
            log.debug("Initialization not completed, deferring meter value message");
            return;
        }

        try {
            // 1. Десериализуем сообщение в объект MeterValueMessage
            MeterValueMessage meterMsg = objectMapper.readValue(message, MeterValueMessage.class);

            // 2. Проверяем наличие sampledValue с measurand = "SO_C"
            boolean hasSoC = meterMsg.getPayload().stream()
                    .flatMap(p -> p.getSampledValue().stream())
                    .anyMatch(sv -> "SO_C".equals(sv.getMeasurand()));

            if (hasSoC) {
                // 3. Создаём событие и публикуем в общий Redis Stream
                StationEventDTO event = StationEventDTO.createMeterValueEvent(
                        meterMsg.getChargeBoxId(),
                        meterMsg
                );

                redisStreamService.publishEvent(event)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                recordId -> log.debug("Meter value event published for station {}: {}",
                                        meterMsg.getChargeBoxId(), recordId),
                                error -> log.error("Failed to publish meter value event: {}", error.getMessage())
                        );
            } else {
                log.trace("Message for station {} has no SoC, skipping", meterMsg.getChargeBoxId());
            }

        } catch (Exception e) {
            log.error("Error processing meter value message: {}", e.getMessage(), e);
        }
    }
}