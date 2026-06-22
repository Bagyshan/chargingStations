package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class BookingConsumptionFilter {

    Instant from;
    Instant to;

    @Builder.Default
    Granularity granularity = Granularity.DAY;

    @Builder.Default
    EnergyGroupBy groupBy = EnergyGroupBy.TOTAL;

    List<String> stationIds;
    List<String> ownerIds;
    List<Integer> connectorIds;
    List<String> userIds;

    @Builder.Default
    List<String> statuses = List.of("COMPLETED");

    Integer timeOfDayFrom;
    Integer timeOfDayTo;
}
