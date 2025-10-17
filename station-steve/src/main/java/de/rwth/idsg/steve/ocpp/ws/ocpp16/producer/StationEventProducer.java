package de.rwth.idsg.steve.ocpp.ws.ocpp16.producer;


import de.rwth.idsg.steve.ocpp.ws.ocpp16.dto.ConnectorCreateEvent;
import de.rwth.idsg.steve.ocpp.ws.ocpp16.dto.StationCreateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import de.rwth.idsg.steve.utils.PropertiesFileLoader;

@Service
@RequiredArgsConstructor
public class StationEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    PropertiesFileLoader p = new PropertiesFileLoader("main.properties");

    public void sendStationCreated(StationCreateEvent event) {
        kafkaTemplate.send(p.getString("kafka.topics.station.requests"), event.getChargeBoxId(), event);
    }

    public void sendConnectorCreated(ConnectorCreateEvent event) {
        kafkaTemplate.send(p.getString("kafka.topics.station.requests"), event.getChargeBoxId(), event);
    }
}
