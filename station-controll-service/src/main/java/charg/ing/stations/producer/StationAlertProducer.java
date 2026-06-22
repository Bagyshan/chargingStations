package charg.ing.stations.producer;

import charg.ing.stations.dto.event.StationAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Публикует алерты о неисправности станции в {@code station.alerts} (потребитель — user-service). */
@Slf4j
@Component
@RequiredArgsConstructor
public class StationAlertProducer {

    private static final String TOPIC = "station.alerts";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(StationAlertEvent event) {
        kafkaTemplate.send(TOPIC, event.getChargeBoxId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish station alert for {}", event.getChargeBoxId(), ex);
                    } else {
                        log.info("Published station alert: {} connector {} status {}",
                                event.getChargeBoxId(), event.getConnectorId(), event.getStatus());
                    }
                });
    }
}
