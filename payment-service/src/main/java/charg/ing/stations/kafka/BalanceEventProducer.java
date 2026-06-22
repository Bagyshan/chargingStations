package charg.ing.stations.kafka;

import charg.ing.stations.dto.kafka.BalanceUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes wallet-balance changes that an active booking must react to.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceEventProducer {

    public static final String TOPIC = "payment.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishBalanceUpdated(BalanceUpdatedEvent event) {
        kafkaTemplate.send(TOPIC, event.getUserId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish balance update for user {}: {}",
                                event.getUserId(), ex.getMessage());
                    } else {
                        log.info("Published balance update for user {}: new balance = {}",
                                event.getUserId(), event.getNewBalance());
                    }
                });
    }
}
