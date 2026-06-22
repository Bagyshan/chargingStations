package de.rwth.idsg.steve.ocpp.ws.ocpp16.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

/**
 * Liveness-сигнал станции, форвардимый в Kafka (топик {@code station.connectivity}), чтобы
 * station-controll-service определял online/offline. {@code eventType} = CONNECTED | DISCONNECTED |
 * HEARTBEAT.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StationConnectivityEvent {
    private String chargeBoxId;
    private String eventType;
    private DateTime timestamp;
}
