package charg.ing.stations.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("charge_box")
public class ChargeBoxEntity {

    @Id
    @Column("id")
    private Integer id;

    @Column("charge_box_id")
    private String chargeBoxId;

    @Column("ocpp_protocol")
    private String ocppProtocol;

    @Column("charge_point_vendor")
    private String chargePointVendor;

    @Column("charge_point_model")
    private String chargePointModel;

    @Column("charge_point_serial_number")
    private String chargePointSerialNumber;

    @Column("charge_box_serial_number")
    private String chargeBoxSerialNumber;

    @Column("firmware_version")
    private String firmwareVersion;

    @Column("iccid")
    private String iccid;

    @Column("imsi")
    private String imsi;

    @Column("meter_type")
    private String meterType;

    @Column("meter_serial_number")
    private String meterSerialNumber;

    @Column("ocpp_tag")
    private String ocppTag;

    @Column("created_at")
    private Instant createdAt;

    @Column("owner_id")
    private String ownerId;

    @Column("power")
    private String power;

    @Column("kw_cost")
    private BigDecimal kwCost;

    @Column("booking_minute_cost")
    private BigDecimal bookingMinuteCost;

    @Column("address_id")
    private Integer addressId;   // если нужно – храним только внешний ключ

    // --- Паритет со station-controll (синхронизируется из station.state / station.connectivity) ---

    @Column("service_status")
    private String serviceStatus;

    @Column("online")
    private Boolean online;

    @Column("last_seen_at")
    private Instant lastSeenAt;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("version")
    private Long version;
}

//{
//    "chargeBoxId": "CP_TEST_3",
//    "ocppProtocol": "ocpp1.6",
//    "chargePointVendor": "Solidstudio",
//    "chargePointModel": "VirtualChargePoint",
//    "chargePointSerialNumber": "S001",
//    "chargeBoxSerialNumber": null,
//    "firmwareVersion": "1.0.0",
//    "iccid": null,
//    "imsi": null,
//    "meterType": null,
//    "meterSerialNumber": null,
//    "actionType": "CHARGE_BOX",
//    "createdAt": 1777234616900
//}