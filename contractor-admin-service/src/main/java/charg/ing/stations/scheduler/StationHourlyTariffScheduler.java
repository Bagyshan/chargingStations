package charg.ing.stations.scheduler;

import charg.ing.stations.dto.StationHourlyTariffEvent;
import charg.ing.stations.dto.StationHourlyTariffsEvent;
import charg.ing.stations.producer.StationHourlyTariffProducer;
import charg.ing.stations.repository.StationHourlyTariffRepository;
import charg.ing.stations.service.ChargeBoxTariffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StationHourlyTariffScheduler {

    private final StationHourlyTariffRepository repository;

    private final ChargeBoxTariffService chargeBoxTariffService;

    private final StationHourlyTariffProducer producer;

    @Scheduled(cron = "0 0 * * * *")
    public void publishCurrentHourTariffs() {

        int currentHour = LocalTime.now().getHour();

        repository
                .findAllByHour(currentHour)

                .map(tariff ->
                        StationHourlyTariffEvent.builder()
                                .stationId(tariff.getStationId())
                                .hour(tariff.getHour())
                                .kwCost(tariff.getKwCost())
                                .bookingMinuteCost(
                                        tariff.getBookingMinuteCost()
                                )
                                .currentTimestamp(
                                        Instant.now()
                                )
                                .build()
                )

                .collectList()

                .filter(list -> !list.isEmpty())

                .flatMap(this::updateAndSend)

                .doOnSuccess(v ->
                        log.info(
                                "Hourly tariffs published. hour={}",
                                currentHour
                        )
                )

                .doOnError(ex ->
                        log.error(
                                "Error publishing tariffs",
                                ex
                        )
                )

                .subscribe();
    }

    private Mono<Void> updateAndSend(List<StationHourlyTariffEvent> tariffs) {
        return chargeBoxTariffService.updateTariffs(tariffs)
                .then(sendTariffs(tariffs));
    }

    private Mono<Void> sendTariffs(
            List<StationHourlyTariffEvent> tariffs
    ) {

        StationHourlyTariffsEvent event =
                StationHourlyTariffsEvent.builder()
                        .tariffs(tariffs)
                        .build();

        return producer.send(event);
    }
}