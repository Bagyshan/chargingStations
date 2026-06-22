package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayloadItem {
    private long timestamp;
    private List<SampledValue> sampledValue;
    private boolean setTimestamp;
    private boolean setSampledValue;
}