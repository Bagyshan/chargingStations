package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StationHourlyTariffEvent {
    private String stationId;
    private Integer hour;
    private BigDecimal kwCost;
    private BigDecimal bookingMinuteCost;
    private Instant currentTimestamp;
}
