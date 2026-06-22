package charg.ing.stations.dto.event;

import lombok.Data;

import java.util.List;

@Data
public class StationHourlyTariffsEvent {

    private List<StationHourlyTariffEvent> tariffs;
}