package charg.ing.stations.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {
    private Integer id;
    private String addressName;
    private Integer chargeBoxCount;
}
