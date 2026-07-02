package charg.ing.stations.service;

import charg.ing.stations.dto.event.BookingEvent;
import charg.ing.stations.dto.event.BookingEventMessage;
import charg.ing.stations.entity.BookingEntity;
import charg.ing.stations.producer.BookingEventProducer;
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
    private final BookingEventProducer bookingEventProducer;

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

        // Проверка истечения времени: бронь исчерпана, когда прошло оплаченное окно.
        // Ориентируемся на remainingBookingEndTime (endedAt для активной брони не заполняется).
        Instant expiry = booking.getRemainingBookingEndTime();
        if (expiry != null && now.isAfter(expiry)) {
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
            // Итоговые суммы для истории и расчёта (как при ручном завершении).
            int totalMinutes = (int) Math.max(minutesElapsed, 1);
            BigDecimal totalSum = booking.getPricePerMinute().multiply(BigDecimal.valueOf(totalMinutes));
            booking.setTotalMinutes(totalMinutes);
            booking.setTotalSum(totalSum);

            BookingEventMessage stopEvent = buildStopEvent(booking, totalMinutes, totalSum);

            action = bookingRepository.save(booking)
                    .then(Mono.fromRunnable(() -> sendEvent(event)))
                    // STOP_RESERVATION → payment-service списывает оплату за прошедшее время.
                    .then(bookingEventProducer.sendBookingEvent(stopEvent));
        } else {
            action = Mono.fromRunnable(() -> sendEvent(event));
        }
        return action;
    }

    private BookingEventMessage buildStopEvent(BookingEntity booking, int totalMinutes, BigDecimal totalSum) {
        return BookingEventMessage.builder()
                .bookingId(booking.getBookingId())
                .stationId(booking.getStationId())
                .connectorId(booking.getConnectorId())
                .userId(booking.getUserId())
                .eventType(BookingEventMessage.EventType.STOP_RESERVATION)
                .totalSum(totalSum)
                .totalMinutes(totalMinutes)
                .startedAt(booking.getStartedAt())
                .endedAt(booking.getEndedAt())
                .build();
    }

    private void sendEvent(BookingEvent event) {
        kafkaTemplate.send("booking.state", event.getUserId().toString(), event);
        log.info("Sent booking event: {}", event.getEventType());
    }
}