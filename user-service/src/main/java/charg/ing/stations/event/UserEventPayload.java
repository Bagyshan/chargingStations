package charg.ing.stations.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEventPayload {
    private UserEvent event;
    private String sourceService = "user-service";
    private String correlationId;
    private String traceId;
}