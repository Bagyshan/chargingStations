package charg.ing.stations.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("booking")
public class BookingEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("booking_id")
    private UUID bookingId;

    @Column("user_id")
    private UUID userId;

    @Column("station_id")
    private String stationId;

    @Column("connector_id")
    private Integer connectorId;

    @Column("price_per_minute")
    private BigDecimal pricePerMinute;

    @Column("total_sum")
    private BigDecimal totalSum;

    @Column("total_minutes")
    private Integer totalMinutes;

    @Column("started_at")
    private Instant startedAt;

    @Column("ended_at")
    private Instant endedAt;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;
}