package de.rwth.idsg.steve.web.api.dto;

import lombok.*;
import org.joda.time.DateTime;

@Getter
@Setter
public class RemoteStartResponse {
    private Integer transactionId;
    private String chargeBoxId;
    private Integer connectorId;
    private String ocppTag;
    private String startValue;
    private DateTime startTimestamp;

    public RemoteStartResponse(
            int id,
            String chargeBoxId,
            Integer connectorId,
            String ocppTag,
            String startValue,
            DateTime startTimestamp
    ) {
        this.transactionId = id;
        this.chargeBoxId = chargeBoxId;
        this.connectorId = connectorId;
        this.ocppTag = ocppTag;
        this.startValue = startValue;
        this.startTimestamp = startTimestamp;
    }
}
