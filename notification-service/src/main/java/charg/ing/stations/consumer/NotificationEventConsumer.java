package charg.ing.stations.consumer;

import charg.ing.stations.event.UserEvent;
import charg.ing.stations.event.enums.UserEventType;
import charg.ing.stations.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

//    @KafkaListener(topics = "${kafka.topics.notification-events:notification.events}",
//                groupId = "${spring.kafka.consumer.group-id}",
//                containerFactory = "kafkaListenerContainerFactory")
//    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
//        log.debug("Received {} notification events", records.size());
//
//        boolean allProcessed = true;
//
//        for (ConsumerRecord<String, String> record : records) {
//            try {
//                String key = record.key();
//                String value = record.value();
//                UserEvent event = objectMapper.readValue(value, UserEvent.class);
//
//                log.info("Processing event: {} for user: {}", event.getEventType(), event.getUserEmail());
//
//                processEvent(event);
//            } catch (Exception e) {
//                log.error("Failed to process record: key={}, value={}", record.key(), record.value(), e);
//                allProcessed = false;
//                // В реальной системе можно отправить в DLT или продолжить обработку остальных
//                // В зависимости от требований: либо прерываем батч, либо продолжаем
//                // Здесь для простоты помечаем, что не все обработано, но продолжаем
//            }
//        }
//
//        if (allProcessed) {
//            ack.acknowledge();
//            log.debug("All events processed successfully, offsets committed");
//        } else {
//            // В зависимости от стратегии: можно закоммитить только успешные? Но Kafka не поддерживает частичный коммит.
//            // Либо отправлять неудачные в DLT и коммитить весь батч.
//            // Реализуем позже через DeadLetterPublishingRecoverer.
//            log.warn("Some events failed, but committing anyway (consider DLT)");
//            ack.acknowledge(); // для демо
//        }
//    }

    @KafkaListener(topics = "${kafka.topics.notification-events:notification.events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.debug("Received {} notification events", records.size());

        boolean allProcessed = true;

        for (ConsumerRecord<String, String> record : records) {
            try {
                String key = record.key();
                String value = record.value();

                log.debug("Raw value: {}", value);

                // ПРОБЛЕМА: value может быть строкой, содержащей JSON внутри
                // Решение: сначала проверить, не является ли value экранированной JSON-строкой
                UserEvent event;

                if (value.startsWith("\"") && value.endsWith("\"")) {
                    // Значение начинается и заканчивается кавычками - это строка, содержащая JSON
                    // Нужно убрать внешние кавычки и разархивировать
                    String unescapedJson = objectMapper.readValue(value, String.class);
                    event = objectMapper.readValue(unescapedJson, UserEvent.class);
                } else {
                    // Обычный JSON
                    event = objectMapper.readValue(value, UserEvent.class);
                }

                log.info("Processing event: {} for user: {}", event.getEventType(), event.getUserEmail());
                processEvent(event);

            } catch (Exception e) {
                log.error("Failed to process record: key={}, value={}", record.key(), record.value(), e);
                allProcessed = false;
            }
        }

        if (allProcessed) {
            ack.acknowledge();
            log.debug("All events processed successfully, offsets committed");
        } else {
            log.warn("Some events failed, but committing anyway (consider DLT)");
            ack.acknowledge();
        }
    }


    private void processEvent(UserEvent event) {
        UserEventType type = event.getEventType();
        String email = event.getUserEmail();
        String token = event.getMetadata() != null ? (String) event.getMetadata().get("token") : null;

        switch (type) {
            case EMAIL_VERIFICATION_REQUESTED:
                if (token == null) {
                    log.error("Missing token for EMAIL_VERIFICATION_REQUESTED, userId={}", event.getUserId());
                    return;
                }
                emailService.sendVerificationEmail(email, token);
                break;
            case PASSWORD_RESET_REQUESTED:
                if (token == null) {
                    log.error("Missing token for PASSWORD_RESET_REQUESTED, userId={}", event.getUserId());
                    return;
                }
                emailService.sendPasswordResetEmail(email, token);
                break;
            case USER_REGISTERED:
                // Например, приветственное письмо
                // emailService.sendWelcomeEmail(email);
                break;
            case STATION_FAULTED:
                emailService.sendStationFaultedEmail(email, event.getMetadata());
                break;
            default:
                log.warn("Unsupported event type: {}", type);
        }
    }
}