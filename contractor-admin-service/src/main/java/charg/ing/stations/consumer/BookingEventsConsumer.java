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
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;

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
        subscription = KafkaReceiver.create(
                receiverFactory.create("contractor-admin-booking-consumer", Set.of("booking.events"))
        )
        .receive()
        .flatMap(record -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) record.value();
            BookingEventMessage event = objectMapper.convertValue(payload, BookingEventMessage.class);

            Mono<Void> processing = "START_RESERVATION".equals(event.getEventType())
                    ? handleStartReservation(event)
                    : handleStopReservation(event);

            return processing
                    .doOnSuccess(v -> record.receiverOffset().acknowledge())
                    .onErrorResume(DataAccessException.class, e -> {
                        log.error("DB error processing booking.events bookingId={} — offset NOT committed, consumer will restart", event.getBookingId(), e);
                        return Mono.error(e);
                    })
                    .onErrorResume(e -> {
                        log.error("Business error processing booking.events bookingId={} — skipping", event.getBookingId(), e);
                        record.receiverOffset().acknowledge();
                        return Mono.empty();
                    });
        })
        .subscribe(
                v -> {},
                e -> log.error("Fatal error in booking.events consumer", e)
        );
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
                                .status(event.getEventType())
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
                    entity.setStatus(event.getEventType());
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
                                    .status(event.getEventType())
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
