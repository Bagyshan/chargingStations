package de.rwth.idsg.steve.ocpp.ws.ocpp16.dto;

import de.rwth.idsg.steve.ocpp.ws.ocpp16.enums.Type;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

/**
 * OCPP StatusNotification forwarded to Kafka (topic {@code station.status}) so station-controll-service
 * can keep the operational connector status live and react to {@code Faulted}/{@code Unavailable}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConnectorStatusEvent {
    private String chargeBoxId;
    private int connectorId;
    private String status;
    private String errorCode;
    private String info;
    private Type actionType;
    private DateTime timestamp;
}
