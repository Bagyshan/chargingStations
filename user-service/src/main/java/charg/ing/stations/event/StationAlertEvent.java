package charg.ing.stations.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Алерт о неисправности станции из station-controll (топик {@code station.alerts}). user-service
 * резолвит получателей (владелец + ADMIN/SPECIALIST) и рассылает им письма через notification.events.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StationAlertEvent {
    private String chargeBoxId;
    private Integer connectorId;
    private String ownerId;
    private String status;
    private String errorCode;
}
