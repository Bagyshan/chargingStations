package charg.ing.stations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class StationCreateEvent {
    private String chargeBoxId;
    private String ocppProtocol;
    private String chargePointVendor;
    private String chargePointModel;
    private String chargePointSerialNumber;
    private String chargeBoxSerialNumber;
    private String firmwareVersion;
    private String iccid;
    private String imsi;
    private String meterType;
    private String meterSerialNumber;
    private String actionType;
    private Long createdAt; // epoch millis

    // getters / setters (omitted for brevity) — сгенерируйте IDE
    // конструктор без аргументов нужен для Jackson
}