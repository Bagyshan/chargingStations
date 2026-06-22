package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingEventMessage {
    private UUID bookingId;
    private String stationId;
    private Integer connectorId;
    private UUID userId;
    private String eventType;
    private BigDecimal totalSum;
    private Integer totalMinutes;
    private Instant startedAt;
    private Instant endedAt;
}
