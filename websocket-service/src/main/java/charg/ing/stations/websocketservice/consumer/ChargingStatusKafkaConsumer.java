package charg.ing.stations.websocketservice.consumer;

import charg.ing.stations.websocketservice.dto.WebSocketMessageDTO;
import charg.ing.stations.websocketservice.dto.charging.ChargingStatusDTO;
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

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Routes live charging status to the initiating user only. Mirrors {@link BookingEventKafkaConsumer}:
 * reads the {@code charging.user.status} topic, resolves the user's websocket session and pushes the
 * enriched meter value (energy, cost, kwCost, maxKwQuantity, startedAt, soc) to that session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargingStatusKafkaConsumer {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.charging-group-id:websocket-service-charging-group}")
    private String groupId;

    @PostConstruct
    public void start() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class); // JSON parsed manually
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ReceiverOptions<String, String> receiverOptions = ReceiverOptions.<String, String>create(props)
                .subscription(Collections.singletonList("charging.user.status"));

        KafkaReceiver.create(receiverOptions)
                .receive()
                .doOnNext(this::processRecord)
                .doOnError(e -> log.error("Error in charging status Kafka consumer", e))
                .retry()
                .subscribe();
    }

    private void processRecord(ReceiverRecord<String, String> record) {
        try {
            ChargingStatusDTO event = objectMapper.readValue(record.value(), ChargingStatusDTO.class);

            String userId = event.getUserId();
            if (userId == null) {
                record.receiverOffset().acknowledge();
                return;
            }

            String clientId = sessionManager.getClientIdByUserId(userId);
            if (clientId == null) {
                log.debug("User {} not connected, skipping charging status for tx {}",
                        userId, event.getTransactionId());
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
                    .chargingEvent(event)
                    .timestamp(Instant.now().toEpochMilli())
                    .build();

            String json = objectMapper.writeValueAsString(wsMessage);
            session.send(Mono.just(session.textMessage(json)))
                    .doOnSuccess(v -> {
                        log.debug("Sent charging status (tx {}) to user {}", event.getTransactionId(), userId);
                        record.receiverOffset().acknowledge();
                    })
                    .doOnError(e -> log.error("Failed to send charging status to user {}: {}",
                            userId, e.getMessage()))
                    .subscribe();

        } catch (Exception e) {
            log.error("Error processing charging status record: {}", e.getMessage(), e);
            record.receiverOffset().acknowledge();
        }
    }
}
