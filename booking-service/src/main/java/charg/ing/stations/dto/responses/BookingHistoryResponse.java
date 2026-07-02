package charg.ing.stations.dto.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Запись истории бронирования пользователя")
public record BookingHistoryResponse(
        @Schema(description = "ID бронирования") UUID bookingId,
        @Schema(description = "ID станции (chargeBoxId)") String stationId,
        @Schema(description = "Номер коннектора") Integer connectorId,
        @Schema(description = "Статус: ACTIVE | COMPLETED | FAILED") String status,
        @Schema(description = "Тариф за минуту брони") BigDecimal pricePerMinute,
        @Schema(description = "Максимум минут по балансу на момент брони") Integer maxBookingMinutes,
        @Schema(description = "Фактически оплачено минут (по завершении)") Integer totalMinutes,
        @Schema(description = "Итоговая сумма (по завершении)") BigDecimal totalSum,
        @Schema(description = "Время начала") Instant startedAt,
        @Schema(description = "Время окончания") Instant endedAt,
        @Schema(description = "Время создания записи") Instant createdAt
) {}
