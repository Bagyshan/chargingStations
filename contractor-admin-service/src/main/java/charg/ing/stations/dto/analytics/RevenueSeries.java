package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RevenueSeries {
    String groupKey;
    String label;
    List<RevenueDataPoint> points;
}
