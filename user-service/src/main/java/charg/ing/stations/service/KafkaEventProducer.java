package charg.ing.stations.service;

import charg.ing.stations.event.UserEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventProducer {

    @Value("${kafka.topics.user-events:user.events}")
    private String userEventsTopic;

    @Value("${kafka.topics.notification-events:notification.events}")
    private String notificationEventsTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Mono<Void> sendUserEvent(UserEvent event) {
        return Mono.fromFuture(CompletableFuture.runAsync(() -> {
            try {
                String eventJson = objectMapper.writeValueAsString(event);

                kafkaTemplate.send(userEventsTopic, event.getUserEmail(), eventJson)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.debug("User event sent successfully: {} for user: {}",
                                        event.getEventType(), event.getUserEmail());
                            } else {
                                log.error("Failed to send user event: {} for user: {}",
                                        event.getEventType(), event.getUserEmail(), ex);
                            }
                        });

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize user event: {}", event, e);
            }
        }));
    }


    public Mono<Void> sendNotificationEvent(UserEvent event) {
        return Mono.create(sink -> {
            try {
                String key = event.getUserEmail();
                String value = objectMapper.writeValueAsString(event);

                kafkaTemplate.send(notificationEventsTopic, key, value)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.debug("Notification event sent: {} for user: {}", event.getEventType(), key);
                                sink.success();
                            } else {
                                log.error("Failed to send notification event: {}", event.getEventType(), ex);
                                sink.error(ex);
                            }
                        });
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize event", e);
                sink.error(e);
            }
        });
    }

}