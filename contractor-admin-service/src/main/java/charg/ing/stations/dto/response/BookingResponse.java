package charg.ing.stations.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Station booking record")
public class BookingResponse {

    @Schema(description = "Database primary key")
    private Long id;

    @Schema(description = "Booking unique identifier")
    private UUID bookingId;

    private UUID userId;
    private String stationId;
    private Integer connectorId;
    private BigDecimal pricePerMinute;
    private BigDecimal totalSum;
    private Integer totalMinutes;
    private Instant startedAt;
    private Instant endedAt;

    @Schema(description = "Booking status", example = "START_RESERVATION",
            allowableValues = {"START_RESERVATION", "STOP_RESERVATION"})
    private String status;

    private Instant createdAt;
}
