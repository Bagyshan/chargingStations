package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class BookingDataPoint {
    Instant periodStart;
    Long bookings;
    BigDecimal totalMinutes;
    BigDecimal avgDurationMinutes;
    BigDecimal revenue;
    List<String> bookingIds;
}
