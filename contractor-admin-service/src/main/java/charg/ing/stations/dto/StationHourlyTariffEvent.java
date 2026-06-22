package charg.ing.stations.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationHourlyTariffEvent {

    private String stationId;

    private Integer hour;

    private BigDecimal kwCost;

    private BigDecimal bookingMinuteCost;

    private Instant currentTimestamp;
}