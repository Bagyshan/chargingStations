package charg.ing.stations.producer;

import charg.ing.stations.dto.event.TransactionEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProducer {

    private static final String TOPIC = "transaction.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TransactionEventMessage message) {
        kafkaTemplate.send(TOPIC, message.getChargeBoxId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish transaction event transactionId={}", message.getTransactionId(), ex);
                    } else {
                        log.info("Published transaction event transactionId={} status={} partition={} offset={}",
                                message.getTransactionId(),
                                message.getStatus(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
