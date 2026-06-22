package charg.ing.stations.dto.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class StationHourlyTariffEvent {

    private String stationId;

    private Integer hour;

    private BigDecimal kwCost;

    private BigDecimal bookingMinuteCost;

    private Instant currentTimestamp;
}