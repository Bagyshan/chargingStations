package citrine.os.station.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class OcppRequest {
    private UUID correlationId;
    private String chargeBoxId;
    private Integer connectorId;
    private String ocppTag;
    private Boolean isStop;
}