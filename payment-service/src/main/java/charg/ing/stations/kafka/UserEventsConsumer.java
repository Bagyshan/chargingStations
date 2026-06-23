package charg.ing.stations.kafka;

import charg.ing.stations.dto.event.UserEventMessage;
import charg.ing.stations.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Слушает {@code user.events} и при регистрации пользователя заводит пустой кошелёк.
 *
 * <p>Продюсер (user-service) пишет value простой JSON-строкой через StringSerializer без
 * type-заголовков, поэтому используем выделенную фабрику со StringDeserializer
 * ({@code userEventsKafkaListenerContainerFactory}) и парсим JSON вручную.
 *
 * <p>Кошелёк ключуется по {@code keycloakId} (UUID, = {@code sub} из JWT) — тем же id,
 * что приходит в {@code /api/v1/balance/{userId}} и в saga оплаты.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventsConsumer {

    private static final String USER_REGISTERED = "USER_REGISTERED";

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.user-events:user.events}",
            groupId = "payment-service-user-events",
            containerFactory = "userEventsKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (value == null || value.isBlank()) {
            return;
        }

        try {
            // value может быть дважды экранированной JSON-строкой ("\"{...}\"") — снимаем внешние кавычки.
            String json = value;
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }

            UserEventMessage event = objectMapper.readValue(json, UserEventMessage.class);

            if (!USER_REGISTERED.equals(event.getEventType())) {
                return; // остальные события нас не интересуют
            }

            String keycloakId = event.getKeycloakId();
            if (keycloakId == null || keycloakId.isBlank()) {
                log.warn("USER_REGISTERED without keycloakId, skipping wallet creation: userId={}, email={}",
                        event.getUserId(), event.getUserEmail());
                return;
            }

            UUID userId;
            try {
                userId = UUID.fromString(keycloakId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid keycloakId UUID '{}' for email={}, skipping", keycloakId, event.getUserEmail());
                return;
            }

            paymentService.createEmptyWallet(userId)
                    .subscribe(
                            wallet -> log.info("Empty wallet ready for user {} (email={})", userId, event.getUserEmail()),
                            err -> log.error("Failed to create wallet for user {} (email={})", userId, event.getUserEmail(), err));

        } catch (Exception e) {
            log.error("Failed to process user.events record: key={}, value={}", record.key(), value, e);
        }
    }
}
