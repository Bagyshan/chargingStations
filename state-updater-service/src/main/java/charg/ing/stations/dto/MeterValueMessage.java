package charg.ing.stations.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeterValueMessage {
    private String chargeBoxId;
    private Integer connectorId;
    private List<PayloadItem> payload;
    private String actionType;
    private long timestamp;
}

//@Data
////@Getter
//@JsonIgnoreProperties(ignoreUnknown = true)
//class PayloadItem {
//    private long timestamp;
//    private List<SampledValue> sampledValue;
//    private boolean setTimestamp;
//    private boolean setSampledValue;
//
//    public List<SampledValue> getSampledValue() {
//        return this.sampledValue;
//    }
//}

//@Data
//@JsonIgnoreProperties(ignoreUnknown = true)
//class SampledValue {
//    private String value;
//    private String context;
//    private String format;
//    private String measurand;
//    private String phase;
//    private String location;
//    private String unit;
//    private boolean setPhase;
//    private boolean setContext;
//    private boolean setFormat;
//    private boolean setMeasurand;
//    private boolean setLocation;
//    private boolean setUnit;
//    private boolean setValue;
//
//    public String getMeasurand() {
//        return this.measurand;
//    }
//}