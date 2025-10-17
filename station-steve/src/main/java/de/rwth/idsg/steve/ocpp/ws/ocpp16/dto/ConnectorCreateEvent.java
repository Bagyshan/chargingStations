package de.rwth.idsg.steve.ocpp.ws.ocpp16.dto;


import de.rwth.idsg.ocpp.jaxb.JodaDateTimeConverter;
import de.rwth.idsg.steve.ocpp.ws.ocpp16.enums.Type;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ocpp.cs._2015._10.ChargePointErrorCode;
import ocpp.cs._2015._10.ChargePointStatus;
import org.joda.time.DateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConnectorCreateEvent {
    private String chargeBoxId;
    private int connectorId;
    private String info;
    private Type actionType;
    private DateTime timestamp;
    private String vendorId;
}
