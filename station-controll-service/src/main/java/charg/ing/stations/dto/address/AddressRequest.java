package charg.ing.stations.dto.address;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddressRequest {
    @NotBlank
    private String addressName;
}
