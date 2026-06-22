package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SampledValue {
    private String value;
    private String context;
    private String format;
    private String measurand;
    private String phase;
    private String location;
    private String unit;
    private boolean setPhase;
    private boolean setContext;
    private boolean setFormat;
    private boolean setMeasurand;
    private boolean setLocation;
    private boolean setUnit;
    private boolean setValue;
}