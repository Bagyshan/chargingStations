package de.rwth.idsg.steve.ocpp.ws.ocpp16.producer;


import de.rwth.idsg.steve.ocpp.ws.ocpp16.dto.ConnectorCreateEvent;
import de.rwth.idsg.steve.ocpp.ws.ocpp16.dto.ConnectorStatusEvent;
import de.rwth.idsg.steve.ocpp.ws.ocpp16.dto.MeterValueEvent;
import de.rwth.idsg.steve.ocpp.ws.ocpp16.dto.StationConnectivityEvent;
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

    public void sendMeterValue(MeterValueEvent event) {
        kafkaTemplate.send(p.getString("kafka.topics.station.meter.values"), event.getChargeBoxId(), event);
    }

    public void sendConnectorStatus(ConnectorStatusEvent event) {
        kafkaTemplate.send(p.getString("kafka.topics.station.status"), event.getChargeBoxId(), event);
    }

    public void sendConnectivity(StationConnectivityEvent event) {
        kafkaTemplate.send(p.getString("kafka.topics.station.connectivity"), event.getChargeBoxId(), event);
    }
}
