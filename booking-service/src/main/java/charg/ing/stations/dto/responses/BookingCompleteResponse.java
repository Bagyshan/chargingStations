package charg.ing.stations.dto.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingCompleteResponse(
        @Schema(description = "ID бронирования") UUID bookingId,
        @Schema(description = "Статус") String status,
        @Schema(description = "Итоговая сумма") BigDecimal totalSum,
        @Schema(description = "Итоговое количество минут бронирования") Integer totalMinutes,
        @Schema(description = "Время начала") Instant startedAt,
        @Schema(description = "Время окончания") Instant endedAt,
        @Schema(description = "Сообщение об ошибке") String errorMessage
) {}
