package charg.ing.stations.websocketservice.dto.charging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Live charging status for the initiating user, consumed from {@code charging.user.status} and pushed
 * to that user's websocket session only. Mirrors station-controll-service's ChargingStatusEvent.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChargingStatusDTO {
    private String userId;
    private String chargeBoxId;
    private Integer connectorId;
    private Integer transactionId;
    private BigDecimal energyKwh;
    private BigDecimal currentCost;
    private BigDecimal kwCost;
    private BigDecimal maxKwQuantity;
    private Instant startedAt;
    private Double soc;
    private String status;
    private Instant timestamp;
}
