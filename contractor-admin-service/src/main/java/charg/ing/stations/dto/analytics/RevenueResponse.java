package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class RevenueResponse {
    Instant from;
    Instant to;
    Granularity granularity;
    EnergyGroupBy groupBy;
    RevenueSummary summary;
    List<RevenueSeries> series;
}
