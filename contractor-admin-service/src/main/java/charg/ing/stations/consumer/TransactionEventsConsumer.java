package charg.ing.stations.consumer;

import charg.ing.stations.config.KafkaReceiverFactory;
import charg.ing.stations.dto.TransactionEventMessage;
import charg.ing.stations.entity.TransactionEntity;
import charg.ing.stations.repository.ConnectorRepository;
import charg.ing.stations.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventsConsumer {

    private final KafkaReceiverFactory receiverFactory;
    private final TransactionRepository transactionRepository;
    private final ConnectorRepository connectorRepository;
    private final ObjectMapper objectMapper;

    private Disposable subscription;

    @EventListener(ApplicationReadyEvent.class)
    public void startConsuming() {
        log.info("Starting TransactionEventsConsumer for topic transaction.events");
        subscription = KafkaReceiver.create(
                receiverFactory.create("contractor-admin-transaction-consumer", Set.of("transaction.events"))
        )
        .receive()
        .flatMap(record -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) record.value();
            TransactionEventMessage event = objectMapper.convertValue(payload, TransactionEventMessage.class);

            Mono<Void> processing = "ACTIVE".equals(event.getStatus())
                    ? handleStartTransaction(event)
                    : handleStopTransaction(event);

            return processing
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())
                    .onErrorResume(DataAccessException.class, e -> {
                        log.error("DB error processing transaction.events transactionId={} — offset NOT committed, consumer will restart", event.getTransactionId(), e);
                        return Mono.error(e);
                    })
                    .onErrorResume(e -> {
                        log.error("Business error processing transaction.events transactionId={} — skipping", event.getTransactionId(), e);
                        record.receiverOffset().acknowledge();
                        return Mono.empty();
                    });
        })
        .subscribe(
                v -> {},
                e -> log.error("Fatal error in transaction.events consumer", e)
        );
    }

    private Mono<Void> handleStartTransaction(TransactionEventMessage event) {
        return transactionRepository.findByTransactionId(event.getId())
                .switchIfEmpty(Mono.defer(() -> {
                    TransactionEntity entity = new TransactionEntity();
                    entity.setTransactionId(event.getId());
                    entity.setChargeBoxId(event.getChargeBoxId());
                    entity.setConnectorId(event.getConnectorId());
                    entity.setStartTimestamp(event.getStartTimestamp());
                    entity.setStartValue(event.getStartValue());
                    entity.setStatus(event.getStatus());
                    entity.setUserId(event.getUserId());
                    entity.setCreatedAt(event.getCreatedAt());
                    return transactionRepository.save(entity);
                }))
                .doOnSuccess(e -> log.info("Saved transaction transactionId={}", e.getTransactionId()))
                .then(updateConnectorStatus(event.getChargeBoxId(), event.getConnectorId(), "Charging"));
    }

    private Mono<Void> handleStopTransaction(TransactionEventMessage event) {
        return transactionRepository.findByTransactionId(event.getId())
                .flatMap(entity -> {
                    entity.setStopTimestamp(event.getStopTimestamp());
                    entity.setStopValue(event.getStopValue());
                    entity.setTransactionValue(event.getTransactionValue());
                    entity.setStatus(event.getStatus());
                    entity.setReason(event.getReason());
                    entity.setUpdatedAt(event.getUpdatedAt());
                    return transactionRepository.save(entity);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Transaction not found for stop event, inserting transactionId={}", event.getId());
                    TransactionEntity entity = new TransactionEntity();
                    entity.setTransactionId(event.getId());
                    entity.setChargeBoxId(event.getChargeBoxId());
                    entity.setConnectorId(event.getConnectorId());
                    entity.setStartTimestamp(event.getStartTimestamp());
                    entity.setStartValue(event.getStartValue());
                    entity.setStopTimestamp(event.getStopTimestamp());
                    entity.setStopValue(event.getStopValue());
                    entity.setTransactionValue(event.getTransactionValue());
                    entity.setStatus(event.getStatus());
                    entity.setReason(event.getReason());
                    entity.setUserId(event.getUserId());
                    entity.setCreatedAt(event.getCreatedAt());
                    entity.setUpdatedAt(event.getUpdatedAt());
                    return transactionRepository.save(entity);
                }))
                .doOnSuccess(e -> log.info("Updated transaction transactionId={}", e.getTransactionId()))
                .then(updateConnectorStatus(event.getChargeBoxId(), event.getConnectorId(), "Available"));
    }

    private Mono<Void> updateConnectorStatus(String chargeBoxId, Integer connectorId, String status) {
        if (chargeBoxId == null || connectorId == null) {
            return Mono.empty();
        }
        return connectorRepository.findByChargeBoxIdAndConnectorId(chargeBoxId, connectorId)
                .flatMap(connector -> {
                    connector.setStatus(status);
                    return connectorRepository.save(connector);
                })
                .doOnSuccess(c -> log.info("Connector status updated chargeBoxId={} connectorId={} status={}", chargeBoxId, connectorId, status))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Connector not found for status update chargeBoxId={} connectorId={}", chargeBoxId, connectorId)
                ))
                .then();
    }
}
