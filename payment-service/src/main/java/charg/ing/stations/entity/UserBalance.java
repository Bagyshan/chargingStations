package charg.ing.stations.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "balance")
public class UserBalance {

    @Id
    @Column("user_id")
    private UUID userId;

    @Column("balance")
    private BigDecimal balance;

    @Column("is_booking")
    private boolean isBooking;

    @Column("is_charging")
    private boolean isCharging;

    /** Backward-compatible constructor (defaults {@code isCharging=false}). */
    public UserBalance(UUID userId, BigDecimal balance, boolean isBooking) {
        this(userId, balance, isBooking, false);
    }
}