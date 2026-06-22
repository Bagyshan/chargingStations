package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RevenueSummary {
    BigDecimal totalRevenue;
    BigDecimal chargingRevenue;
    BigDecimal bookingRevenue;

    Long totalChargingSessions;
    Long totalBookings;

    BigDecimal avgChargingRevenuePerSession;
    BigDecimal avgBookingRevenuePerBooking;

    Long uniqueStations;
    Long uniqueUsers;
}
