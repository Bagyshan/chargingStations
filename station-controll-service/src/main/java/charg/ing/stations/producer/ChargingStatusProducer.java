package charg.ing.stations.producer;

import charg.ing.stations.dto.event.ChargingStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes per-user live charging status to {@code charging.user.status}, keyed by userId so
 * websocket-service can route it to the initiating user only (mirrors {@link TransactionEventProducer}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChargingStatusProducer {

    private static final String TOPIC = "charging.user.status";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(ChargingStatusEvent event) {
        if (event.getUserId() == null) {
            return; // no initiator to route to (e.g. charger-initiated session)
        }
        kafkaTemplate.send(TOPIC, event.getUserId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish charging status transactionId={}",
                                event.getTransactionId(), ex);
                    } else {
                        log.debug("Published charging status user={} transactionId={} energyKwh={} cost={}",
                                event.getUserId(), event.getTransactionId(),
                                event.getEnergyKwh(), event.getCurrentCost());
                    }
                });
    }
}
