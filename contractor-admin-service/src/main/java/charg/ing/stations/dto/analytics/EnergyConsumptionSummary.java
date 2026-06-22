package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class EnergyConsumptionSummary {
    BigDecimal totalEnergyKwh;
    Long totalSessions;
    BigDecimal avgEnergyPerSessionKwh;
    BigDecimal avgSessionDurationMinutes;
    BigDecimal totalRevenue;
    Long uniqueStations;
    Long uniqueUsers;
}
