package charg.ing.stations.service;

import charg.ing.stations.dto.event.BookingEvent;
import charg.ing.stations.dto.event.BookingEventMessage;
import charg.ing.stations.dto.responses.BookingCompleteResponse;
import charg.ing.stations.entity.BookingEntity;
import charg.ing.stations.producer.BookingEventProducer;
import charg.ing.stations.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCompletionService {

    private final BookingRepository bookingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BookingEventProducer bookingEventProducer;

    public Mono<BookingCompleteResponse> completeBooking(UUID userId, UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .switchIfEmpty(Mono.error(new RuntimeException("Booking not found")))
                .flatMap(booking -> {
                    if (!booking.getUserId().equals(userId)) {
                        return Mono.error(new RuntimeException("Booking does not belong to user"));
                    }
                    if (!"ACTIVE".equals(booking.getStatus())) {
                        return Mono.error(new RuntimeException("Booking is not active"));
                    }

                    Instant now = Instant.now();
                    booking.setStatus("COMPLETED");
                    booking.setEndedAt(now);

                    BookingEvent bookingEvent = sendCompletionEvent(booking, now);

                    booking.setTotalSum(bookingEvent.getData().getCurrentCost());
                    booking.setTotalMinutes(bookingEvent.getData().getMinutesElapsed());

                    BookingEventMessage event = buildStopEvent(booking);
//                    bookingEventProducer.sendBookingEvent(event);

                    return bookingRepository.save(booking)
                            .flatMap(saved ->
                                    bookingEventProducer.sendBookingEvent(event)
                                            .thenReturn(saved)
                            )
//                            .doOnSuccess(saved -> sendCompletionEvent(saved, now))
                            .map(saved -> new BookingCompleteResponse(
                                    saved.getBookingId(),
                                    saved.getStatus(),
                                    saved.getTotalSum(),
                                    saved.getTotalMinutes(),
                                    saved.getStartedAt(),
                                    saved.getEndedAt(),
                                    null
                            ));
                });
    }

    private BookingEvent sendCompletionEvent(BookingEntity booking, Instant completedAt) {
        Instant now = Instant.now();
        // Вычисляем текущие метрики
        long minutesElapsed = 0;
//        if (booking.getStartedAt() != null) {
//        }
        minutesElapsed = Duration.between(booking.getStartedAt(), now).toMinutes();
//        minutesElapsed = Math.min(minutesElapsed, booking.getMaxBookingMinutes());

//        log.error("Minutes elapsed: {}", minutesElapsed);

        BigDecimal currentCost = booking.getPricePerMinute()
                .multiply(BigDecimal.valueOf(minutesElapsed));
        int remainingMinutes = booking.getMaxBookingMinutes() - (int) minutesElapsed;
        if (remainingMinutes < 0) remainingMinutes = 0;


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

        BookingEvent event = BookingEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(BookingEvent.EventType.RESERVATION_COMPLETED)
                .timestamp(completedAt)
                .userId(booking.getUserId())
                .reservationId(booking.getBookingId())
                .data(data)
                .build();

        kafkaTemplate.send("booking.state", event.getReservationId().toString(), event);

        return event;
    }

    private BookingEventMessage buildStopEvent(BookingEntity booking) {
        long minutes = Duration.between(booking.getStartedAt(), booking.getEndedAt()).toMinutes();
        int totalMinutes = (int) Math.max(minutes, 1); // минимум 1 минута
        BigDecimal totalSum = booking.getPricePerMinute().multiply(BigDecimal.valueOf(totalMinutes));
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
}