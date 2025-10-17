package de.rwth.idsg.steve.web.api.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;

@Getter
@Setter
public class RemoteStopResponse {
    private Integer transactionId;
    private String chargeBoxId;
    private Integer connectorId;
    private String ocppTag;
    private String startValue;
    private String stopValue;
    private DateTime stopTimestamp;

    public RemoteStopResponse(
            int id,
            String chargeBoxId,
            Integer connectorId,
            String ocppTag,
            @Nullable String startValue,
            @Nullable String stopValue,
            DateTime stopTimestamp
    ) {
        this.transactionId = id;
        this.chargeBoxId = chargeBoxId;
        this.connectorId = connectorId;
        this.ocppTag = ocppTag;
        this.startValue = startValue;
        this.stopValue = stopValue;
        this.stopTimestamp = stopTimestamp;
    }
}
