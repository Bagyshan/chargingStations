package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class EnergyConsumptionResponse {
    Instant from;
    Instant to;
    Granularity granularity;
    EnergyGroupBy groupBy;
    EnergyConsumptionSummary summary;
    List<EnergySeries> series;
}
