package charg.ing.stations.producer;

import charg.ing.stations.dto.event.BookingEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventProducer {

    private final KafkaSender<String, Object> kafkaSender;

    public Mono<Void> sendBookingEvent(BookingEventMessage event) {
        SenderRecord<String, Object, String> record = SenderRecord.create(
                new ProducerRecord<>("booking.events", event.getUserId().toString(), event),
                event.getUserId().toString()
        );
        return kafkaSender.send(Mono.just(record))
                .doOnNext(result -> log.debug("Booking event sent: {}", event.getEventType()))
                .doOnError(e -> log.error("Failed to send booking event: {}", e.getMessage()))
                .then();
    }
}