package charg.ing.stations.dto;

import charg.ing.stations.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Запись истории зарядок пользователя. Плоский DTO без ленивых связей
 * ({@code connector}/{@code chargeBox}) — чтобы безопасно сериализовать вне транзакции.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionHistoryDTO {
    private Integer transactionId;
    private String chargeBoxId;
    private Integer connectorId;
    private TransactionStatus status;
    private String reason;
    private Instant startTimestamp;
    private Instant stopTimestamp;
    private Integer startValue;   // Wh (meter start)
    private Integer stopValue;    // Wh (meter stop)
    private Integer transactionValue; // Wh consumed
    private BigDecimal totalSum;      // сом
    private BigDecimal pricePerKwh;   // сом/кВт·ч
    private BigDecimal maxKwQuantity; // лимит кВт·ч по балансу
    private Instant createdAt;
}
