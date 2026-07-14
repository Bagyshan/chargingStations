package charg.ing.stations.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Charging transaction record")
public class TransactionResponse {

    @Schema(description = "Database primary key")
    private Long id;

    @Schema(description = "OCPP transaction identifier")
    private Long transactionId;

    private String chargeBoxId;
    private Integer connectorId;
    private Instant startTimestamp;
    private Integer startValue;
    private Instant stopTimestamp;
    private Integer stopValue;

    @Schema(description = "Energy consumed in Wh (stopValue - startValue)")
    private Integer transactionValue;

    @Schema(description = "Стоимость зарядки, сом. Если оплата не сведена (total_sum пуст) — "
            + "оценка: энергия(кВт·ч) × тариф (price_per_kwh, иначе тариф станции)")
    private BigDecimal totalSum;

    @Schema(description = "Transaction status", example = "ACTIVE",
            allowableValues = {"ACTIVE", "COMPLETED", "CANCELLED", "REJECTED"})
    private String status;

    private String reason;
    private String userId;
    private Instant createdAt;
    private Instant updatedAt;
}
