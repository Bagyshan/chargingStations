package charg.ing.stations.websocketservice.dto.meter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SampledValue {
    @JsonProperty("value")
    private String value;

    @JsonProperty("context")
    private String context;

    @JsonProperty("format")
    private String format;

    @JsonProperty("measurand")
    private String measurand;

    @JsonProperty("phase")
    private String phase;

    @JsonProperty("location")
    private String location;

    @JsonProperty("unit")
    private String unit;

    @JsonProperty("setPhase")
    private boolean setPhase;

    @JsonProperty("setContext")
    private boolean setContext;

    @JsonProperty("setFormat")
    private boolean setFormat;

    @JsonProperty("setMeasurand")
    private boolean setMeasurand;

    @JsonProperty("setLocation")
    private boolean setLocation;

    @JsonProperty("setUnit")
    private boolean setUnit;

    @JsonProperty("setValue")
    private boolean setValue;
}