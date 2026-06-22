package charg.ing.stations.controller;

import charg.ing.stations.dto.analytics.*;
import charg.ing.stations.service.RevenueAnalyticsService;
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
@Tag(name = "Revenue Analytics", description = "Combined revenue analytics from charging and booking")
public class RevenueAnalyticsController {

    private final RevenueAnalyticsService service;

    @GetMapping("/revenue")
    @Operation(summary = "Get combined revenue chart data",
            description = """
                    Returns time-series revenue data combining charging (transaction.total_sum)
                    and booking (booking.total_sum) income in one chart.
                    Each data point breaks revenue into chargingRevenue + bookingRevenue.
                    Use includeCharging / includeBooking flags to show only one source.
                    """)
    public Mono<ResponseEntity<RevenueResponse>> getRevenue(

            @Parameter(description = "Start of period (ISO-8601). Default: 30 days ago")
            @RequestParam(required = false) Instant from,

            @Parameter(description = "End of period (ISO-8601). Default: now")
            @RequestParam(required = false) Instant to,

            @Parameter(description = "Time granularity: HOUR, DAY, WEEK, MONTH, YEAR")
            @RequestParam(defaultValue = "DAY") Granularity granularity,

            @Parameter(description = "Group series by: TOTAL, STATION, OWNER")
            @RequestParam(defaultValue = "TOTAL") EnergyGroupBy groupBy,

            @Parameter(description = "Include charging revenue. Default: true")
            @RequestParam(defaultValue = "true") boolean includeCharging,

            @Parameter(description = "Include booking revenue. Default: true")
            @RequestParam(defaultValue = "true") boolean includeBooking,

            @Parameter(description = "Filter by station IDs (comma-separated)")
            @RequestParam(required = false) List<String> stationIds,

            @Parameter(description = "Filter by contractor/owner IDs (comma-separated)")
            @RequestParam(required = false) List<String> ownerIds,

            @Parameter(description = "Filter by connector IDs (comma-separated)")
            @RequestParam(required = false) List<Integer> connectorIds,

            @Parameter(description = "Filter by user IDs (comma-separated)")
            @RequestParam(required = false) List<String> userIds,

            @Parameter(description = "Transaction statuses for charging. Default: COMPLETED")
            @RequestParam(required = false) List<String> transactionStatuses,

            @Parameter(description = "Booking statuses. Default: COMPLETED")
            @RequestParam(required = false) List<String> bookingStatuses,

            @Parameter(description = "UTC hour of day range start (0–23)")
            @RequestParam(required = false) Integer timeOfDayFrom,

            @Parameter(description = "UTC hour of day range end (0–23)")
            @RequestParam(required = false) Integer timeOfDayTo
    ) {
        Instant resolvedFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant resolvedTo   = to   != null ? to   : Instant.now();

        RevenueFilter filter = RevenueFilter.builder()
                .from(resolvedFrom)
                .to(resolvedTo)
                .granularity(granularity)
                .groupBy(groupBy)
                .includeCharging(includeCharging)
                .includeBooking(includeBooking)
                .stationIds(stationIds)
                .ownerIds(ownerIds)
                .connectorIds(connectorIds)
                .userIds(userIds)
                .transactionStatuses(transactionStatuses != null ? transactionStatuses : List.of("COMPLETED"))
                .bookingStatuses(bookingStatuses != null ? bookingStatuses : List.of("COMPLETED"))
                .timeOfDayFrom(timeOfDayFrom)
                .timeOfDayTo(timeOfDayTo)
                .build();

        return service.getRevenue(filter)
                .map(ResponseEntity::ok);
    }
}
