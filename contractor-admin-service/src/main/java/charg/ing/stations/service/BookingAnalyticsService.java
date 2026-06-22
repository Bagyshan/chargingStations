package charg.ing.stations.service;

import charg.ing.stations.dto.analytics.*;
import charg.ing.stations.repository.BookingAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingAnalyticsService {

    private final BookingAnalyticsRepository repository;

    public Mono<BookingConsumptionResponse> getBookingConsumption(BookingConsumptionFilter filter) {
        log.debug("Booking analytics request: from={} to={} granularity={} groupBy={}",
                filter.getFrom(), filter.getTo(), filter.getGranularity(), filter.getGroupBy());

        Mono<BookingConsumptionSummary> summaryMono = repository.querySummary(filter)
                .map(this::mapSummary)
                .defaultIfEmpty(emptySummary());

        Mono<List<BookingSeries>> seriesMono = repository.queryChartData(filter)
                .collectList()
                .map(rows -> buildSeries(rows, filter.getGroupBy()));

        return Mono.zip(summaryMono, seriesMono)
                .map(tuple -> BookingConsumptionResponse.builder()
                        .from(filter.getFrom())
                        .to(filter.getTo())
                        .granularity(filter.getGranularity())
                        .groupBy(filter.getGroupBy())
                        .summary(tuple.getT1())
                        .series(tuple.getT2())
                        .build());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private BookingConsumptionSummary mapSummary(Map<String, Object> row) {
        return BookingConsumptionSummary.builder()
                .totalBookings(toLong(row.get("total_bookings")))
                .completedBookings(toLong(row.get("completed_bookings")))
                .cancelledBookings(toLong(row.get("cancelled_bookings")))
                .totalMinutes(toDecimal(row.get("total_minutes")))
                .avgDurationMinutes(toDecimal(row.get("avg_duration_minutes")))
                .totalRevenue(toDecimal(row.get("total_revenue")))
                .uniqueStations(toLong(row.get("unique_stations")))
                .uniqueUsers(toLong(row.get("unique_users")))
                .build();
    }

    private List<BookingSeries> buildSeries(List<Map<String, Object>> rows, EnergyGroupBy groupBy) {
        Map<String, List<BookingDataPoint>> seriesMap = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String key = Objects.toString(row.get("group_key"), "TOTAL");
            seriesMap.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(mapDataPoint(row));
        }

        List<BookingSeries> result = new ArrayList<>(seriesMap.size());
        seriesMap.forEach((key, points) ->
                result.add(BookingSeries.builder()
                        .groupKey(key)
                        .label(buildLabel(key, groupBy))
                        .points(points)
                        .build())
        );
        return result;
    }

    private BookingDataPoint mapDataPoint(Map<String, Object> row) {
        return BookingDataPoint.builder()
                .periodStart(toInstant(row.get("period")))
                .bookings(toLong(row.get("bookings")))
                .totalMinutes(toDecimal(row.get("total_minutes")))
                .avgDurationMinutes(toDecimal(row.get("avg_duration_minutes")))
                .revenue(toDecimal(row.get("total_revenue")))
                .bookingIds(toStringIds(row.get("booking_ids")))
                .build();
    }

    private String buildLabel(String groupKey, EnergyGroupBy groupBy) {
        return switch (groupBy) {
            case TOTAL   -> "Total";
            case STATION -> "Station " + groupKey;
            case OWNER   -> "Contractor " + groupKey;
        };
    }

    private BookingConsumptionSummary emptySummary() {
        return BookingConsumptionSummary.builder()
                .totalBookings(0L).completedBookings(0L).cancelledBookings(0L)
                .totalMinutes(BigDecimal.ZERO).avgDurationMinutes(BigDecimal.ZERO)
                .totalRevenue(BigDecimal.ZERO).uniqueStations(0L).uniqueUsers(0L)
                .build();
    }

    // ── Type helpers ──────────────────────────────────────────────────────────

    private BigDecimal toDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd.setScale(3, RoundingMode.HALF_UP);
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(3, RoundingMode.HALF_UP);
        try { return new BigDecimal(value.toString()).setScale(3, RoundingMode.HALF_UP); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); }
        catch (Exception e) { return 0L; }
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC);
        return null;
    }

    private List<String> toStringIds(Object value) {
        if (value == null) return List.of();
        String csv = value.toString().trim();
        if (csv.isEmpty()) return List.of();
        return Arrays.asList(csv.split(","));
    }
}
