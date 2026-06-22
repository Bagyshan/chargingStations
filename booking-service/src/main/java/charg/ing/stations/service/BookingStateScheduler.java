package charg.ing.stations.service;

import charg.ing.stations.dto.event.BookingEvent;
import charg.ing.stations.entity.BookingEntity;
import charg.ing.stations.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingStateScheduler {

    private final BookingRepository bookingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 10000) // каждые 10 секунд
    public void processActiveBookings() {
        bookingRepository.findByStatus("ACTIVE")
                .flatMap(this::processBooking)
                .doOnError(e -> log.error("Error in scheduled booking processing", e))
                .subscribe();
    }
    private Mono<Void> processBooking(BookingEntity booking) {
        Instant now = Instant.now();
        boolean completed = false;

        // Проверка истечения времени
        if (booking.getEndedAt() != null && now.isAfter(booking.getEndedAt())) {
            booking.setStatus("COMPLETED");
            booking.setEndedAt(now); // фактическое завершение
            completed = true;
        }

        // Вычисляем текущие метрики
        long minutesElapsed = 0;
        if (booking.getStartedAt() != null) {
            minutesElapsed = Duration.between(booking.getStartedAt(), now).toMinutes();
        }
        minutesElapsed = Math.min(minutesElapsed, booking.getMaxBookingMinutes());

//        log.error("Minutes elapsed: {}", minutesElapsed);

        BigDecimal currentCost = booking.getPricePerMinute()
                .multiply(BigDecimal.valueOf(minutesElapsed));
        int remainingMinutes = booking.getMaxBookingMinutes() - (int) minutesElapsed;
        if (remainingMinutes < 0) remainingMinutes = 0;

        // Формируем событие
        BookingEvent.EventData data = BookingEvent.EventData.builder()
                .stationId(booking.getStationId())
                .connectorId(booking.getConnectorId())
                .pricePerMinute(booking.getPricePerMinute())
                .minutesElapsed((int) minutesElapsed)
                .currentCost(currentCost)
                .maxBookingMinutes(booking.getMaxBookingMinutes())
                .remainingBookingMinutes(remainingMinutes)
                .startedAt(booking.getStartedAt())
                .estimatedEndTime(booking.getRemainingBookingEndTime())
                .build();

        BookingEvent.EventType eventType = completed ?
                BookingEvent.EventType.RESERVATION_COMPLETED :
                BookingEvent.EventType.RESERVATION_PROGRESS;

        BookingEvent event = BookingEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(now)
                .userId(booking.getUserId())
                .reservationId(booking.getBookingId())
                .data(data)
                .build();

        Mono<Void> action;
        if (completed) {
            action = bookingRepository.save(booking)
                    .then(Mono.fromRunnable(() -> sendEvent(event)));
        } else {
            action = Mono.fromRunnable(() -> sendEvent(event));
        }
        return action;
    }

    private void sendEvent(BookingEvent event) {
        kafkaTemplate.send("booking.state", event.getUserId().toString(), event);
        log.info("Sent booking event: {}", event.getEventType());
    }
}