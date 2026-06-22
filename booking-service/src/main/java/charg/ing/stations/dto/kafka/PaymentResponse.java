package charg.ing.stations.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID requestId;
    private UUID userId;
    private BigDecimal balance;
    private boolean success;
    private String errorMessage;
}