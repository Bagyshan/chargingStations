package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class BookingConsumptionSummary {
    Long totalBookings;
    Long completedBookings;
    Long cancelledBookings;
    BigDecimal totalMinutes;
    BigDecimal avgDurationMinutes;
    BigDecimal totalRevenue;
    Long uniqueStations;
    Long uniqueUsers;
}
