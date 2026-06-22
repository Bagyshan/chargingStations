package charg.ing.stations.websocketservice.dto.meter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayloadItem {
    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("sampledValue")
    private List<SampledValue> sampledValue;

    @JsonProperty("setTimestamp")
    private boolean setTimestamp;

    @JsonProperty("setSampledValue")
    private boolean setSampledValue;
}