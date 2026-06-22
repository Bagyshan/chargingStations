package de.rwth.idsg.steve.ocpp.ws.ocpp16.dto;

import de.rwth.idsg.steve.ocpp.ws.ocpp16.enums.Type;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ocpp.cs._2015._10.MeterValue;
import ocpp.cs._2015._10.MeterValuesRequest;
import org.joda.time.DateTime;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MeterValueEvent {
    private String chargeBoxId;
    private int connectorId;
    private List<MeterValue> payload;
    private Type actionType;
    private DateTime timestamp;
}
