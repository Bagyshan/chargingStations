package charg.ing.stations.websocketservice.dto.booking;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingEventDTO {
    private UUID eventId;
    private String eventType;       // например, "RESERVATION_PROGRESS"
    private Instant timestamp;
    private UUID userId;
    private UUID reservationId;
    private BookingEventData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookingEventData {
        private String stationId;
        private Integer connectorId;
        private Double pricePerMinute;
        private Integer minutesElapsed;
        private Double currentCost;
        private Integer maxBookingMinutes;
        private Integer remainingBookingMinutes;
        private Instant startedAt;
        private Instant estimatedEndTime;
    }
}
