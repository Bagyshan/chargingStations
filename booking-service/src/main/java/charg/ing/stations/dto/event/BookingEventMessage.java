package charg.ing.stations.dto.event;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BookingEventMessage {
    private UUID bookingId;
    private String stationId;
    private Integer connectorId;
    private UUID userId;
    private EventType eventType;
    private BigDecimal totalSum;
    private Integer totalMinutes;
    private Instant startedAt;
    private Instant endedAt;

    public enum EventType {
        START_RESERVATION,
        STOP_RESERVATION
    }
}