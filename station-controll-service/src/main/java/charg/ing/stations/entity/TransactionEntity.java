package charg.ing.stations.entity;

import charg.ing.stations.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Integer transactionId;

    @Column(name = "charge_box_id", nullable = false)
    private String chargeBoxId;

    @Column(name = "connector_id", nullable = false)
    private Integer connectorId;

    @Column(name = "start_timestamp")
    private Instant startTimestamp;

    @Column(name = "start_value")
    private Integer startValue;

    @Column(name = "stop_timestamp")
    private Instant stopTimestamp;

    @Column(name = "stop_value")
    private Integer stopValue;

    @Column(name = "transaction_value")
    private Integer transactionValue;

    @Column(name = "status", length = 50)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "charge_box_id", referencedColumnName = "charge_box_id", insertable = false, updatable = false),
        @JoinColumn(name = "connector_id", referencedColumnName = "connector_id", insertable = false, updatable = false)
    })
    private ConnectorEntity connector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_box_id", referencedColumnName = "charge_box_id", insertable = false, updatable = false)
    private ChargeBoxEntity chargeBox;
}