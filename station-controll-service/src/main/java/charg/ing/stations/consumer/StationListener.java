package charg.ing.stations.consumer;


import charg.ing.stations.service.factory.EventServiceFactory;
import charg.ing.stations.service.interfaces.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@Slf4j
@Component
public class StationListener {

    private final EventServiceFactory eventServiceFactory;

    @Autowired
    public StationListener(EventServiceFactory eventServiceFactory) {
        this.eventServiceFactory = eventServiceFactory;
    }

    @KafkaListener(
            topics = {"station.requests"},
            groupId = "station-controller-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(
            @Payload Map<String, Object> message,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        try {
            String actionType = getActionType(message);
            log.error("📨 Processing message with action type: {}", actionType); // log.info
            EventService service = eventServiceFactory.getService(actionType);
            log.error("📨 Selected service: {}", service.getClass().getSimpleName()); // log.info

            // Вызываем транзакционный метод
            service.processEvent(message, ack, topic);

            // Если все прошло успешно, подтверждаем сообщение
            ack.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Unknown action type: {}", e.getMessage());
            // В случае этой ошибки мы тоже не хотим повторять обработку, поэтому подтверждаем
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process message: {}", e.getMessage(), e);
            // ВАЖНО: НЕ подтверждаем сообщение здесь. Позволяем Kafka повторить попытку.
            // Если бы вы подтвердили, транзакция в сервисе откатилась бы, а сообщение потерялось бы.
        }
    }

    private String getActionType(Map<String, Object> message) {
        Object actionType = message.get("actionType");
        if (actionType == null) {
            throw new IllegalArgumentException("actionType is required");
        }
        return actionType.toString();
    }
}
