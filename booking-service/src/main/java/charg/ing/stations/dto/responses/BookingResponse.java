package charg.ing.stations.dto.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Результат бронирования")
public record BookingResponse(
        @Schema(description = "ID бронирования") UUID bookingId,
        @Schema(description = "Статус") String status,
        @Schema(description = "Максимальное количество минут") Integer maxBookingMinutes,
        @Schema(description = "Время начала") Instant startedAt,
        @Schema(description = "Примерное время окончания") Instant remainingBookingEndTime,
        @Schema(description = "Время окончания") Instant endedAt,
        @Schema(description = "Сообщение об ошибке (только при статусе FAILED)") String errorMessage
) {}