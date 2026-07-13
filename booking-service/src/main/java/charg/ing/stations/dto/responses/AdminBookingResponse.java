package charg.ing.stations.dto.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Бронирование для админ-панели (все пользователи). */
@Builder
@Schema(description = "Запись бронирования для администрирования")
public record AdminBookingResponse(
        UUID bookingId,
        UUID userId,
        String stationId,
        Integer connectorId,
        String status,
        BigDecimal pricePerMinute,
        Integer totalMinutes,
        BigDecimal totalSum,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt
) {}
