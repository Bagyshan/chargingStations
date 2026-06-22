package charg.ing.stations.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingEvent {
    private UUID eventId;
    private EventType eventType;
    private Instant timestamp;
    private UUID userId;
    private UUID reservationId;
    private EventData data;

    public enum EventType {
        RESERVATION_CREATED,
        RESERVATION_STARTED,
        RESERVATION_PROGRESS,
        RESERVATION_BALANCE_UPDATED,
        RESERVATION_COMPLETED,
        RESERVATION_CANCELLED,
        RESERVATION_PAYMENT_FAILED
    }

    @Data
    @Builder
    public static class EventData {
        private String stationId;
        private Integer connectorId;
        private BigDecimal pricePerMinute;
        private Integer minutesElapsed;
        private BigDecimal currentCost;
        private Integer maxBookingMinutes;
        private Integer remainingBookingMinutes;
        private Instant startedAt;
        private Instant estimatedEndTime;
    }
}