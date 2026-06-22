package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationStateWithDistance {
    @JsonUnwrapped
    private StationStateDTO station;
    private Double distanceTo; // расстояние в километрах
}