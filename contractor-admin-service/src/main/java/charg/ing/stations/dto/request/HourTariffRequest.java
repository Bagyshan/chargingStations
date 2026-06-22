package charg.ing.stations.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record HourTariffRequest(

        @NotNull
        @Min(0)
        @Max(23)
        Integer hour,

        @NotNull
        BigDecimal kwCost,

        @NotNull
        BigDecimal bookingMinuteCost

) {
}