package charg.ing.stations.dto;


import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Пример структуры сообщения:
 * {
 *   "requestId": "uuid",
 *   "type": "get_balance" | "top_up",
 *   "userId": "uuid",
 *   "amount": 100.50
 * }
 */
@Data
@NoArgsConstructor
public class BookingPaymentRequest {
    private String requestId;
    private String type; // get_balance | top_up
    private UUID userId;
    private BigDecimal amount;
}