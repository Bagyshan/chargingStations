package charg.ing.stations.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceDto(UUID userId, BigDecimal balance, boolean isBooking) {}