package charg.ing.stations.consumer;


import charg.ing.stations.service.factory.EventServiceFactory;
import charg.ing.stations.service.interfaces.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StationListener {

    private final EventServiceFactory eventServiceFactory;

    @Autowired
    public StationListener(EventServiceFactory eventServiceFactory) {
        this.eventServiceFactory = eventServiceFactory;
    }

    @KafkaListener(
            topics = {"station.requests", "ocpp.responses"},
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
            EventService service = eventServiceFactory.getService(actionType);
            service.processEvent(message, ack, topic);

        } catch (IllegalArgumentException e) {
            System.err.println("Unknown action type: " + e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            System.err.println("Failed to process message: " + e.getMessage());
            ack.acknowledge();
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
