package charg.ing.stations.consumer;

import charg.ing.stations.dto.event.StationHourlyTariffsEvent;
import charg.ing.stations.service.StationTariffUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StationHourlyTariffConsumer {

    private final StationTariffUpdateService tariffUpdateService;

    @KafkaListener(
            topics = "station.hourly.tariffs",
            containerFactory = "stationTariffListenerFactory"
    )
    public void consume(
            StationHourlyTariffsEvent event,
            Acknowledgment acknowledgment
    ) {

        try {

            log.info(
                    "Received {} station tariffs",
                    event.getTariffs().size()
            );

            tariffUpdateService.updateTariffs(
                    event.getTariffs()
            );

            acknowledgment.acknowledge();

        } catch (Exception ex) {

            log.error(
                    "Error processing tariffs",
                    ex
            );

            throw ex;
        }
    }
}