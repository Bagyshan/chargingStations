package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class RevenueDataPoint {
    Instant periodStart;

    BigDecimal totalRevenue;
    BigDecimal chargingRevenue;
    BigDecimal bookingRevenue;

    Long chargingSessions;
    Long bookingCount;

    List<String> transactionIds;
    List<String> bookingIds;
}
