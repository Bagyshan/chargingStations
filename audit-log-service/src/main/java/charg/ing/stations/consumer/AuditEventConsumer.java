package charg.ing.stations.consumer;

import charg.ing.stations.config.KafkaReceiverFactory;
import charg.ing.stations.dto.AuditEventMessage;
import charg.ing.stations.entity.AuditEvent;
import charg.ing.stations.repository.AuditEventRepository;
import charg.ing.stations.service.AuditIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Реактивный консьюмер топика {@code audit.events}: конвертирует событие и пишет его
 * в Elasticsearch. Порядок гарантий:
 * <ul>
 *   <li>перед приёмом дожидается создания индекса ({@link AuditIndexService});</li>
 *   <li>дедуп — {@code eventId} становится {@code _id} документа (повтор перезапишет);</li>
 *   <li>оффсет коммитим только после успешной записи в ES (at-least-once);</li>
 *   <li>битые сообщения пропускаем (ack), транзиентные ошибки ES — не коммитим и
 *       перезапускаем приём с backoff (уцелевшие оффсеты перечитаются).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final KafkaReceiverFactory receiverFactory;
    private final AuditEventRepository repository;
    private final AuditIndexService indexService;
    private final ObjectMapper objectMapper;

    @Value("${audit.kafka.topic}")
    private String topic;

    @Value("${audit.kafka.group-id}")
    private String groupId;

    private Disposable subscription;

    @EventListener(ApplicationReadyEvent.class)
    public void startConsuming() {
        log.info("Starting AuditEventConsumer for topic {} (group {})", topic, groupId);

        // Свежий receiver на каждую (пере)подписку — чтобы retry поднимал новый consumer.
        Flux<ReceiverRecord<String, Object>> records = Flux.defer(() ->
                KafkaReceiver.create(receiverFactory.create(groupId, Set.of(topic))).receive());

        subscription = indexService.ensureIndex()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn("Waiting for Elasticsearch to be ready for audit index (attempt {})",
                                rs.totalRetries() + 1)))
                .thenMany(records)
                .flatMap(this::handle)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn("Restarting audit.events consumer after error: {}",
                                String.valueOf(rs.failure()))))
                .subscribe(
                        v -> {},
                        e -> log.error("audit.events consumer terminated unexpectedly", e));
    }

    private Mono<Void> handle(ReceiverRecord<String, Object> record) {
        AuditEventMessage msg;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) record.value();
            msg = objectMapper.convertValue(value, AuditEventMessage.class);
        } catch (Exception e) {
            log.error("Malformed audit event at offset={} partition={} — skipping",
                    record.offset(), record.partition(), e);
            record.receiverOffset().acknowledge();
            return Mono.empty();
        }

        if (msg.getEventId() == null || msg.getEventId().isBlank()) {
            log.warn("Audit event without eventId at offset={} — skipping", record.offset());
            record.receiverOffset().acknowledge();
            return Mono.empty();
        }

        AuditEvent entity = toEntity(msg);
        return repository.save(entity)
                .doOnSuccess(saved -> {
                    log.debug("Stored audit event id={} type={} action={} source={}",
                            saved.getEventId(), saved.getEventType(), saved.getAction(), saved.getSource());
                    record.receiverOffset().acknowledge();
                })
                .onErrorResume(DataAccessException.class, e -> {
                    // Транзиентная ошибка ES: оффсет НЕ коммитим, пробрасываем — внешний
                    // retryWhen перезапустит приём, событие перечитается (дедуп по _id спасёт).
                    log.error("Elasticsearch error storing audit event id={} — offset NOT committed", msg.getEventId(), e);
                    return Mono.error(e);
                })
                .then();
    }

    private AuditEvent toEntity(AuditEventMessage m) {
        Instant now = Instant.now();
        return AuditEvent.builder()
                .eventId(m.getEventId())
                .timestamp(m.getTimestamp() != null ? m.getTimestamp() : now)
                .eventType(m.getEventType())
                .action(m.getAction())
                .userId(m.getUserId())
                .source(m.getSource())
                .severity(m.getSeverity() != null ? m.getSeverity() : "INFO")
                .entityId(m.getEntityId())
                .correlationId(m.getCorrelationId())
                .ip(m.getIp())
                .userAgent(m.getUserAgent())
                .message(m.getMessage())
                .payload(m.getPayload())
                .receivedAt(now)
                .build();
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
