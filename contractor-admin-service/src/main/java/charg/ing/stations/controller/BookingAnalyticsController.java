package charg.ing.stations.controller;

import charg.ing.stations.dto.analytics.*;
import charg.ing.stations.service.BookingAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Booking Analytics", description = "Booking analytics with flexible filtering")
public class BookingAnalyticsController {

    private final BookingAnalyticsService service;

    @GetMapping("/bookings")
    @Operation(summary = "Get booking analytics chart data",
            description = """
                    Returns time-series booking data with summary statistics.
                    Metrics: bookings count, total minutes, avg duration, revenue.
                    Supports the same grouping and filtering as energy analytics.
                    """)
    public Mono<ResponseEntity<BookingConsumptionResponse>> getBookingConsumption(

            @Parameter(description = "Start of period (ISO-8601). Default: 30 days ago")
            @RequestParam(required = false) Instant from,

            @Parameter(description = "End of period (ISO-8601). Default: now")
            @RequestParam(required = false) Instant to,

            @Parameter(description = "Time granularity: HOUR, DAY, WEEK, MONTH, YEAR")
            @RequestParam(defaultValue = "DAY") Granularity granularity,

            @Parameter(description = "Group series by: TOTAL, STATION, OWNER")
            @RequestParam(defaultValue = "TOTAL") EnergyGroupBy groupBy,

            @Parameter(description = "Filter by station IDs (comma-separated)")
            @RequestParam(required = false) List<String> stationIds,

            @Parameter(description = "Filter by contractor/owner IDs (comma-separated)")
            @RequestParam(required = false) List<String> ownerIds,

            @Parameter(description = "Filter by connector IDs (comma-separated)")
            @RequestParam(required = false) List<Integer> connectorIds,

            @Parameter(description = "Filter by user IDs (comma-separated)")
            @RequestParam(required = false) List<String> userIds,

            @Parameter(description = "Filter by statuses: COMPLETED, CANCELLED, ACTIVE, REJECTED")
            @RequestParam(required = false) List<String> statuses,

            @Parameter(description = "UTC hour of day range start (0–23)")
            @RequestParam(required = false) Integer timeOfDayFrom,

            @Parameter(description = "UTC hour of day range end (0–23)")
            @RequestParam(required = false) Integer timeOfDayTo
    ) {
        Instant resolvedFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant resolvedTo   = to   != null ? to   : Instant.now();

        BookingConsumptionFilter filter = BookingConsumptionFilter.builder()
                .from(resolvedFrom)
                .to(resolvedTo)
                .granularity(granularity)
                .groupBy(groupBy)
                .stationIds(stationIds)
                .ownerIds(ownerIds)
                .connectorIds(connectorIds)
                .userIds(userIds)
                // Если клиент не задал статусы — НЕ фильтруем по статусу (считаем все брони периода).
                // Раньше по умолчанию стоял только COMPLETED, и при иных статусах в мироре
                // (ACTIVE / старые STOP_RESERVATION) аналитика приходила пустой.
                .statuses(statuses)
                .timeOfDayFrom(timeOfDayFrom)
                .timeOfDayTo(timeOfDayTo)
                .build();

        return service.getBookingConsumption(filter)
                .map(ResponseEntity::ok);
    }
}
