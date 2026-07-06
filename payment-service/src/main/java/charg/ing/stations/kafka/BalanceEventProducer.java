package charg.ing.stations.kafka;

import charg.ing.stations.dto.kafka.BalanceUpdatedEvent;
import charg.ing.stations.dto.kafka.TopUpCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes wallet-balance changes that an active booking must react to,
 * plus top-up completions for push notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceEventProducer {

    public static final String TOPIC = "payment.events";
    public static final String TOPUP_TOPIC = "payment.topup.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Every credited top-up → notification-service pushes «Кошелёк пополнен». */
    public void publishTopUpCompleted(TopUpCompletedEvent event) {
        kafkaTemplate.send(TOPUP_TOPIC, event.getUserId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish top-up event for user {}: {}",
                                event.getUserId(), ex.getMessage());
                    } else {
                        log.info("Published top-up event for user {}: +{} (balance {})",
                                event.getUserId(), event.getAmount(), event.getNewBalance());
                    }
                });
    }

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
