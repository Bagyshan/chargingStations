package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class EnergySeries {
    /** Ключ группировки: station ID, owner ID, или "TOTAL" */
    String groupKey;
    /** Человекочитаемая метка */
    String label;
    List<EnergyDataPoint> points;
}
