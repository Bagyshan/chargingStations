package charg.ing.stations.service;

import charg.ing.stations.dto.StationEventDTO;
import charg.ing.stations.dto.StationHourlyTariffEvent;
import charg.ing.stations.dto.StationHourlyTariffsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaTariffConsumerService {

    private final RedisVersionedService redisVersionedService;
    private final RedisStreamService redisStreamService;

    @KafkaListener(
            topics = "station.hourly.tariffs",
            containerFactory = "tariffListenerContainerFactory",
            groupId = "state-updater-tariff-group"
    )
    public void consumeHourlyTariffs(StationHourlyTariffsEvent event, Acknowledgment ack) {
        if (event == null || event.getTariffs() == null || event.getTariffs().isEmpty()) {
            ack.acknowledge();
            return;
        }

        List<StationHourlyTariffEvent> tariffs = event.getTariffs();
        log.info("Received hourly tariffs update for {} stations, hour={}",
                tariffs.size(), tariffs.get(0).getHour());

        // Параллельно обновляем все станции в Redis, собираем успешно обновлённые
        Flux.fromIterable(tariffs)
                .flatMap(tariff -> redisVersionedService.updateStationTariffs(tariff)
                        .map(updatedState -> tariff)
                        .onErrorResume(e -> {
                            log.error("Failed to update tariff for station={}: {}",
                                    tariff.getStationId(), e.getMessage());
                            return reactor.core.publisher.Mono.empty();
                        })
                )
                .collectList()
                .flatMap(updatedTariffs -> {
                    if (updatedTariffs.isEmpty()) {
                        log.warn("No stations were updated in Redis — skipping stream publish");
                        return reactor.core.publisher.Mono.empty();
                    }
                    log.info("Updated {}/{} stations in Redis, publishing batch event to stream",
                            updatedTariffs.size(), tariffs.size());
                    // Одно batch-событие вместо N отдельных
                    return redisStreamService.publishEvent(
                            StationEventDTO.createTariffsBatchUpdatedEvent(updatedTariffs)
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(recordId -> {
                    if (recordId != null) {
                        log.debug("Tariff batch event published to stream, recordId={}", recordId);
                    }
                    ack.acknowledge();
                })
                .doOnError(e -> {
                    log.error("Error in hourly tariff consumer: {}", e.getMessage(), e);
                    ack.acknowledge();
                })
                .subscribe();
    }
}
