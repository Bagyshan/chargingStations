package charg.ing.stations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationHourlyTariffsEvent {

    private List<StationHourlyTariffEvent> tariffs;
}