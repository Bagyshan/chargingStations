package charg.ing.stations.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
public class BookingPaymentResponse {
    private String requestId;
    private UUID userId;
    private BigDecimal balance;
    private boolean isBooking;
    private String status; // OK | ERROR
    private String errorMessage;
}
