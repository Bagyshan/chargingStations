package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class EnergyDataPoint {
    Instant periodStart;
    BigDecimal energyKwh;
    Long sessions;
    BigDecimal avgDurationMinutes;
    BigDecimal revenue;
    List<Long> transactionIds;
}
