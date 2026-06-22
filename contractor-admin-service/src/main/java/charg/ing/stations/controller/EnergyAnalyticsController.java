package charg.ing.stations.controller;

import charg.ing.stations.dto.analytics.*;
import charg.ing.stations.service.EnergyAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Energy Analytics", description = "Energy consumption analytics with flexible filtering")
public class EnergyAnalyticsController {

    private final EnergyAnalyticsService service;

    @GetMapping("/energy")
    @Operation(summary = "Get energy consumption chart data",
            description = """
                    Returns time-series energy consumption data with summary statistics.
                    Results are grouped by the specified granularity (HOUR/DAY/WEEK/MONTH/YEAR)
                    and can be split by STATION or OWNER for multi-series charts.
                    """)
    public Mono<ResponseEntity<EnergyConsumptionResponse>> getEnergyConsumption(

            @Parameter(description = "Start of period (ISO-8601). Default: 30 days ago")
            @RequestParam(required = false) Instant from,

            @Parameter(description = "End of period (ISO-8601). Default: now")
            @RequestParam(required = false) Instant to,

            @Parameter(description = "Time granularity: HOUR, DAY, WEEK, MONTH, YEAR")
            @RequestParam(defaultValue = "DAY") Granularity granularity,

            @Parameter(description = "Group series by: TOTAL, STATION, OWNER")
            @RequestParam(defaultValue = "TOTAL") EnergyGroupBy groupBy,

            @Parameter(description = "Filter by station IDs (comma-separated)")
            @RequestParam(required = false) List<String> chargeBoxIds,

            @Parameter(description = "Filter by contractor/owner IDs (comma-separated)")
            @RequestParam(required = false) List<String> ownerIds,

            @Parameter(description = "Filter by connector IDs (comma-separated)")
            @RequestParam(required = false) List<Integer> connectorIds,

            @Parameter(description = "Filter by user IDs (comma-separated)")
            @RequestParam(required = false) List<String> userIds,

            @Parameter(description = "Filter by transaction statuses. Default: COMPLETED")
            @RequestParam(required = false) List<String> statuses,

            @Parameter(description = "UTC hour of day range start (0–23)")
            @RequestParam(required = false) Integer timeOfDayFrom,

            @Parameter(description = "UTC hour of day range end (0–23)")
            @RequestParam(required = false) Integer timeOfDayTo
    ) {
        Instant resolvedFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant resolvedTo   = to   != null ? to   : Instant.now();

        EnergyConsumptionFilter filter = EnergyConsumptionFilter.builder()
                .from(resolvedFrom)
                .to(resolvedTo)
                .granularity(granularity)
                .groupBy(groupBy)
                .chargeBoxIds(chargeBoxIds)
                .ownerIds(ownerIds)
                .connectorIds(connectorIds)
                .userIds(userIds)
                .statuses(statuses != null ? statuses : List.of("COMPLETED"))
                .timeOfDayFrom(timeOfDayFrom)
                .timeOfDayTo(timeOfDayTo)
                .build();

        return service.getEnergyConsumption(filter)
                .map(ResponseEntity::ok);
    }
}
