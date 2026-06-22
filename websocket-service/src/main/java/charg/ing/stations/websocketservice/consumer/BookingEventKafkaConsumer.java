package charg.ing.stations.websocketservice.consumer;

import charg.ing.stations.websocketservice.dto.WebSocketMessageDTO;
import charg.ing.stations.websocketservice.dto.booking.BookingEventDTO;
import charg.ing.stations.websocketservice.handler.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventKafkaConsumer {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:websocket-service-group}")
    private String groupId;

    @PostConstruct
    public void start() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class); // мы сами десериализуем JSON
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // управляем коммитом вручную

        ReceiverOptions<String, String> receiverOptions = ReceiverOptions.<String, String>create(props)
                .subscription(Collections.singletonList("booking.state"));

        KafkaReceiver.create(receiverOptions)
                .receive()
                .doOnNext(this::processRecord)
                .doOnError(e -> log.error("Error in Kafka consumer", e))
                .retry() // бесконечные попытки переподключения
                .subscribe();
    }

    private void processRecord(ReceiverRecord<String, String> record) {
        try {
            String value = record.value();
            BookingEventDTO event = objectMapper.readValue(value, BookingEventDTO.class);

            String userId = event.getUserId().toString();
            String clientId = sessionManager.getClientIdByUserId(userId);

            if (clientId == null) {
                log.debug("User {} not connected, skipping event {}", userId, event.getEventId());
                record.receiverOffset().acknowledge();
                return;
            }

            WebSocketSession session = sessionManager.getSession(clientId);
            if (session == null || !session.isOpen()) {
                log.warn("Session for user {} is not open, cleaning up", userId);
                sessionManager.unregisterUserSession(clientId);
                record.receiverOffset().acknowledge();
                return;
            }

            WebSocketMessageDTO wsMessage = WebSocketMessageDTO.builder()
                    .type(WebSocketMessageDTO.MessageType.EVENT)
                    .bookingEvent(event) // предполагается, что event может быть напрямую помещён в DTO
                    .timestamp(Instant.now().toEpochMilli())
                    .build();

            String json = objectMapper.writeValueAsString(wsMessage);
            session.send(Mono.just(session.textMessage(json)))
                    .doOnSuccess(v -> {
                        log.debug("Sent booking event {} to user {}", event.getEventType(), userId);
                        record.receiverOffset().acknowledge();
                    })
                    .doOnError(e -> {
                        log.error("Failed to send message to user {}: {}", userId, e.getMessage());
                        // Не подтверждаем offset, чтобы сообщение осталось в Kafka для повторной попытки
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Error processing Kafka record: {}", e.getMessage(), e);
            // Если ошибка десериализации, лучше пропустить сообщение
            record.receiverOffset().acknowledge();
        }
    }
}