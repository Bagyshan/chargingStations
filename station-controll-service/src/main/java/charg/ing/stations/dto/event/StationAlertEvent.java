package charg.ing.stations.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Алерт о неисправности станции, публикуется в топик {@code station.alerts}. user-service резолвит
 * получателей (владелец {@code ownerId} + все ADMIN/SPECIALIST) и шлёт им письма через
 * {@code notification.events}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationAlertEvent {
    private String chargeBoxId;
    private Integer connectorId;
    /** Keycloak-subject владельца станции (ChargeBoxEntity.ownerId), может быть null. */
    private String ownerId;
    private String status;     // Faulted / Unavailable
    private String errorCode;  // OCPP ChargePointErrorCode, может быть null
    private Instant timestamp;
}
