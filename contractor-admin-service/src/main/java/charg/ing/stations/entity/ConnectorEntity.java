package charg.ing.stations.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("connector")
public class ConnectorEntity {

    @Id
    private Integer id;

    @Column("charge_box_id")
    private String chargeBoxId;

    @Column("connector_id")
    private Integer connectorId;

    @Column("info")
    private String info;

    @Column("created_at")
    private Instant createdAt;

    @Column("vendor_id")
    private String vendorId;

    @Column("status")
    private String status;   // длина 20

    @Column("connector_type_id")
    private Integer connectorTypeId;

    // --- Паритет со station-controll (синхронизируется из station.state / station.status) ---

    @Column("charging_user_id")
    private String chargingUserId;

    @Column("booking_user_id")
    private String bookingUserId;

    @Column("version")
    private Long version;
}

//{
//    "chargeBoxId": "CP_TEST_3",
//    "connectorId": 1,
//    "info": null,
//    "actionType": "CONNECTOR",
//    "timestamp": null,
//    "vendorId": null
//}