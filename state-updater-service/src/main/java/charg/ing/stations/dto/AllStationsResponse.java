package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllStationsResponse {
    private List<StationStateWithDistance> stations;
    private String error; // Опционально, для сообщений об ошибках

    public static AllStationsResponse of(List<StationStateWithDistance> stations) {
        return AllStationsResponse.builder()
                .stations(stations)
                .build();
    }

    public static AllStationsResponse of(String error) {
        return AllStationsResponse.builder()
                .stations(List.of()) // Пустой список
                .error(error)
                .build();
    }
}