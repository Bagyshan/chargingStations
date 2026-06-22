package charg.ing.stations.dto.meter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SampledValue {
    @JsonProperty("value")
    private String value;

    @JsonProperty("measurand")
    private String measurand;

    @JsonProperty("unit")
    private String unit;
}
