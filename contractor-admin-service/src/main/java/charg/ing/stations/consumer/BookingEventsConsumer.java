package charg.ing.stations.consumer;

import charg.ing.stations.config.KafkaReceiverFactory;
import charg.ing.stations.dto.BookingEventMessage;
import charg.ing.stations.entity.BookingEntity;
import charg.ing.stations.repository.BookingRepository;
import charg.ing.stations.repository.ConnectorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventsConsumer {

    private final KafkaReceiverFactory receiverFactory;
    private final BookingRepository bookingRepository;
    private final ConnectorRepository connectorRepository;
    private final ObjectMapper objectMapper;

    private Disposable subscription;

    @EventListener(ApplicationReadyEvent.class)
    public void startConsuming() {
        log.info("Starting BookingEventsConsumer for topic booking.events");
        // Flux.defer + retryWhen: если стрим падает (ошибка БД/брокера) — пере-подписываемся
        // и продолжаем. Раньше любая ошибка (в т.ч. один «ядовитый» запис) убивала консьюмер
        // навсегда, и мирор бронирований оставался пустым.
        subscription = Flux.defer(() ->
                        KafkaReceiver.create(
                                receiverFactory.create("contractor-admin-booking-consumer", Set.of("booking.events"))
                        ).receive()
                )
                .concatMap(this::handleRecord)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(rs -> log.warn(
                                "booking.events consumer stream failed — resubscribing (attempt {}): {}",
                                rs.totalRetries() + 1, String.valueOf(rs.failure()))))
                .subscribe(
                        v -> {},
                        e -> log.error("booking.events consumer permanently stopped", e)
                );
    }

    /**
     * Обработка одной записи. Парсинг вынесен ВНУТРЬ реактивной цепочки, поэтому
     * «битое» сообщение (не Map / чужой формат / null) не роняет весь стрим:
     *  - ошибка БД → offset НЕ коммитим, пробрасываем (стрим пере-подпишется, запись переобработается);
     *  - любая другая ошибка → логируем, коммитим offset и пропускаем запись.
     */
    private Mono<Void> handleRecord(ReceiverRecord<String, Object> record) {
        return Mono.defer(() -> {
                    BookingEventMessage event = parse(record);
                    Mono<Void> processing = "START_RESERVATION".equals(event.getEventType())
                            ? handleStartReservation(event)
                            : handleStopReservation(event);
                    return processing.doOnSuccess(v -> record.receiverOffset().acknowledge());
                })
                .onErrorResume(DataAccessException.class, Mono::error)
                .onErrorResume(e -> {
                    log.error("Skipping bad booking.events record (offset={}): {}",
                            record.offset(), e.toString());
                    record.receiverOffset().acknowledge();
                    return Mono.empty();
                });
    }

    private BookingEventMessage parse(ReceiverRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) record.value();
        return objectMapper.convertValue(payload, BookingEventMessage.class);
    }

    private Mono<Void> handleStartReservation(BookingEventMessage event) {
        return bookingRepository.findByBookingId(event.getBookingId())
                .switchIfEmpty(Mono.defer(() -> bookingRepository.save(
                        BookingEntity.builder()
                                .bookingId(event.getBookingId())
                                .userId(event.getUserId())
                                .stationId(event.getStationId())
                                .connectorId(event.getConnectorId())
                                .totalSum(event.getTotalSum())
                                .totalMinutes(event.getTotalMinutes())
                                .startedAt(event.getStartedAt())
                                .endedAt(event.getEndedAt())
                                // Каноничный статус (ACTIVE/COMPLETED), а не тип события — иначе
                                // аналитика (фильтр по COMPLETED) не находит завершённые брони.
                                .status("ACTIVE")
                                .createdAt(Instant.now())
                                .build()
                )))
                .doOnSuccess(e -> log.info("Saved booking bookingId={}", e.getBookingId()))
                .then(updateConnectorStatus(event.getStationId(), event.getConnectorId(), "Reserved"));
    }

    private Mono<Void> handleStopReservation(BookingEventMessage event) {
        return bookingRepository.findByBookingId(event.getBookingId())
                .flatMap(entity -> {
                    entity.setEndedAt(event.getEndedAt());
                    entity.setTotalSum(event.getTotalSum());
                    entity.setTotalMinutes(event.getTotalMinutes());
                    // Завершённая бронь → COMPLETED (аналитика фильтрует именно по нему).
                    entity.setStatus("COMPLETED");
                    return bookingRepository.save(entity);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Booking not found for STOP_RESERVATION, inserting bookingId={}", event.getBookingId());
                    return bookingRepository.save(
                            BookingEntity.builder()
                                    .bookingId(event.getBookingId())
                                    .userId(event.getUserId())
                                    .stationId(event.getStationId())
                                    .connectorId(event.getConnectorId())
                                    .totalSum(event.getTotalSum())
                                    .totalMinutes(event.getTotalMinutes())
                                    .startedAt(event.getStartedAt())
                                    .endedAt(event.getEndedAt())
                                    .status("COMPLETED")
                                    .createdAt(Instant.now())
                                    .build()
                    );
                }))
                .doOnSuccess(e -> log.info("Updated booking bookingId={}", e.getBookingId()))
                .then(updateConnectorStatus(event.getStationId(), event.getConnectorId(), "Available"));
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
