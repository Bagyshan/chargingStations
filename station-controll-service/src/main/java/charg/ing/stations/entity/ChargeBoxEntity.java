package charg.ing.stations.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Transaction;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "charge_box", uniqueConstraints = @UniqueConstraint(columnNames = {"charge_box_id"}))
public class ChargeBoxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "charge_box_id", nullable = false, unique = true)
    private String chargeBoxId;

    @Column(name = "ocpp_protocol")
    private String ocppProtocol;

    @Column(name = "charge_point_vendor")
    private String chargePointVendor;

    @Column(name = "charge_point_model")
    private String chargePointModel;

    @Column(name = "charge_point_serial_number")
    private String chargePointSerialNumber;

    @Column(name = "charge_box_serial_number")
    private String chargeBoxSerialNumber;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "iccid")
    private String iccid;

    @Column(name = "imsi")
    private String imsi;

    @Column(name = "meter_type")
    private String meterType;

    @Column(name = "meter_serial_number")
    private String meterSerialNumber;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "created_at")
    private Instant createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "chargeBox", fetch = FetchType.EAGER)
    private List<ConnectorEntity> connectors;

    @OneToMany(mappedBy = "chargeBox")
    private List<TransactionEntity> transactions;

    // getters / setters
}