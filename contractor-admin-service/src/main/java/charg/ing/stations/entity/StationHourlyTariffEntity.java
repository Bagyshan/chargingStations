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
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("station_hourly_tariffs")
public class StationHourlyTariffEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("station_id")
    private String stationId;

    @Column("hour")
    private Integer hour;

    @Column("kw_cost")
    private BigDecimal kwCost;

    @Column("booking_minute_cost")
    private BigDecimal bookingMinuteCost;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}