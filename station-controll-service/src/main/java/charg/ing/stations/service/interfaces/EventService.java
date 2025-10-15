package charg.ing.stations.service.interfaces;

import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

public interface EventService {
    void processEvent(Map<String, Object> message, Acknowledgment ack, String topic);
}
