package charg.ing.stations.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction")
public class TransactionEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("transaction_id")
    private Long transactionId;

    @Column("charge_box_id")
    private String chargeBoxId;

    @Column("connector_id")
    private Integer connectorId;

    @Column("start_timestamp")
    private Instant startTimestamp;

    @Column("start_value")
    private Integer startValue;

    @Column("stop_timestamp")
    private Instant stopTimestamp;

    @Column("stop_value")
    private Integer stopValue;

    @Column("transaction_value")
    private Integer transactionValue;

    @Column("status")
    private String status;

    @Column("reason")
    private String reason;

    @Column("user_id")
    private String userId;

    @Column("total_sum")
    private BigDecimal totalSum;

    @Column("price_per_kwh")
    private BigDecimal pricePerKwh;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}