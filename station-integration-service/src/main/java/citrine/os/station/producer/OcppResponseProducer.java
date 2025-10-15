package citrine.os.station.producer;


import citrine.os.station.dto.OcppStartResponse;
import citrine.os.station.dto.OcppStopResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OcppResponseProducer {

    private final KafkaTemplate<String, OcppStartResponse> kafkaStartTemplate;
    private final KafkaTemplate<String, OcppStopResponse> kafkaStopTemplate;

    @Value("${topics.ocpp.responses}")
    private String topicName;

    public void sendStartResponse(OcppStartResponse response) {
        kafkaStartTemplate.send(topicName, response.getChargeBoxId()+"/"+response.getConnectorId(), response)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        System.err.println("❌ Ошибка при отправке в Kafka: " + ex.getMessage());
                    } else {
                        System.out.println("✅ Отправлено в Kafka: " +
                                "topic=" + topicName +
                                ", partition=" + result.getRecordMetadata().partition() +
                                ", offset=" + result.getRecordMetadata().offset());
                    }
                });
    }

    public void sendStopResponse(OcppStopResponse response) {
        kafkaStopTemplate.send(topicName, response.getChargeBoxId()+"/"+response.getConnectorId(), response)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        System.err.println("❌ Ошибка при отправке в Kafka: " + ex.getMessage());
                    } else {
                        System.out.println("✅ Отправлено в Kafka: " +
                                "topic=" + topicName +
                                ", partition=" + result.getRecordMetadata().partition() +
                                ", offset=" + result.getRecordMetadata().offset());
                    }
                });
    }
}
