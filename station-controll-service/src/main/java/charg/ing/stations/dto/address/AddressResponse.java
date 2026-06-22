package charg.ing.stations.dto.address;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressResponse {
    private Integer id;
    private String addressName;
    private Integer chargeBoxCount;
}
