package charg.ing.stations.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-user live charging status pushed to {@code charging.user.status} and routed by websocket-service
 * to the charging initiator only. Carries both the raw meter reading (energyKwh, soc) and the
 * cost fields that only station-controll-service can compute from the {@code transaction} row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStatusEvent {

    private String userId;
    private String chargeBoxId;
    private Integer connectorId;
    private Integer transactionId;

    /** Consumed energy since start, kWh = (registerWh - startValue) / 1000. Null if no energy sample. */
    private BigDecimal energyKwh;

    /** Current cost = energyKwh * kwCost. Null if energy/price unknown. */
    private BigDecimal currentCost;

    /** Price per kWh (transaction.price_per_kwh). Null for free charging. */
    private BigDecimal kwCost;

    /** Prepaid kWh budget (transaction.max_kw_quantity). Null for uncapped charging. */
    private BigDecimal maxKwQuantity;

    private Instant startedAt;

    /** State of charge percent, if the meter value carried a SO_C sample. */
    private Double soc;

    private String status;

    private Instant timestamp;
}
