package charg.ing.stations.dto.request;

import charg.ing.stations.enums.ServiceStatus;
import lombok.Data;

/** Тело запроса на смену административного статуса станции. */
@Data
public class ServiceStatusRequest {
    private ServiceStatus serviceStatus;
}
