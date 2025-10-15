package charg.ing.stations.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "connector", uniqueConstraints = @UniqueConstraint(columnNames = {"charge_box_id", "connector_id"}))
public class ConnectorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "charge_box_id", nullable = false)
    private String chargeBoxId;

    @Column(name = "connector_id")
    private int connectorId;

    @Column(name = "info")
    private String info;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "vendor_id")
    private String vendorId;

    @Column(name = "status", length = 20)
    private String status;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_box_id", referencedColumnName = "charge_box_id", nullable = false,
    insertable = false, updatable = false
    )
    private ChargeBoxEntity chargeBox;

    @OneToMany(mappedBy = "connector")
    private List<TransactionEntity> transactions;
}
