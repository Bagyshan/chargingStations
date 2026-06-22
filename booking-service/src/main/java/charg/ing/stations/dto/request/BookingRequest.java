package charg.ing.stations.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Запрос на бронирование станции")
public record BookingRequest(
        @NotBlank @Schema(description = "ID станции", example = "CHARGER_001") String stationId,
        @NotNull @Schema(description = "ID коннектора", example = "1") Integer connectorId
) {}