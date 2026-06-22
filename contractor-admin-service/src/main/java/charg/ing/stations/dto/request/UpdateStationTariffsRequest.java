package charg.ing.stations.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateStationTariffsRequest(

        @NotEmpty
        List<@Valid HourTariffRequest> tariffs

) {
}