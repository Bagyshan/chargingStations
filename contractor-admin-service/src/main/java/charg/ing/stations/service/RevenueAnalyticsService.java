package charg.ing.stations.service;

import charg.ing.stations.dto.analytics.*;
import charg.ing.stations.repository.RevenueAnalyticsRepository;
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
public class RevenueAnalyticsService {

    private final RevenueAnalyticsRepository repository;

    public Mono<RevenueResponse> getRevenue(RevenueFilter filter) {
        log.debug("Revenue analytics request: from={} to={} granularity={} groupBy={} charging={} booking={}",
                filter.getFrom(), filter.getTo(), filter.getGranularity(), filter.getGroupBy(),
                filter.isIncludeCharging(), filter.isIncludeBooking());

        Mono<RevenueSummary> summaryMono = repository.querySummary(filter)
                .map(this::mapSummary)
                .defaultIfEmpty(emptySummary());

        Mono<List<RevenueSeries>> seriesMono = repository.queryChartData(filter)
                .collectList()
                .map(rows -> buildSeries(rows, filter.getGroupBy()));

        return Mono.zip(summaryMono, seriesMono)
                .map(tuple -> RevenueResponse.builder()
                        .from(filter.getFrom())
                        .to(filter.getTo())
                        .granularity(filter.getGranularity())
                        .groupBy(filter.getGroupBy())
                        .summary(tuple.getT1())
                        .series(tuple.getT2())
                        .build());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private RevenueSummary mapSummary(Map<String, Object> row) {
        BigDecimal chargingRevenue = toDecimal(row.get("total_charging_revenue"));
        BigDecimal bookingRevenue = toDecimal(row.get("total_booking_revenue"));
        long chargingSessions = toLong(row.get("total_charging_sessions"));
        long bookings = toLong(row.get("total_bookings"));

        BigDecimal avgCharging = chargingSessions > 0
                ? chargingRevenue.divide(BigDecimal.valueOf(chargingSessions), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgBooking = bookings > 0
                ? bookingRevenue.divide(BigDecimal.valueOf(bookings), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return RevenueSummary.builder()
                .totalRevenue(chargingRevenue.add(bookingRevenue))
                .chargingRevenue(chargingRevenue)
                .bookingRevenue(bookingRevenue)
                .totalChargingSessions(chargingSessions)
                .totalBookings(bookings)
                .avgChargingRevenuePerSession(avgCharging)
                .avgBookingRevenuePerBooking(avgBooking)
                .uniqueStations(toLong(row.get("unique_stations")))
                .uniqueUsers(toLong(row.get("unique_users")))
                .build();
    }

    private List<RevenueSeries> buildSeries(List<Map<String, Object>> rows, EnergyGroupBy groupBy) {
        Map<String, List<RevenueDataPoint>> seriesMap = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String key = Objects.toString(row.get("group_key"), "TOTAL");
            seriesMap.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(mapDataPoint(row));
        }

        List<RevenueSeries> result = new ArrayList<>(seriesMap.size());
        seriesMap.forEach((key, points) ->
                result.add(RevenueSeries.builder()
                        .groupKey(key)
                        .label(buildLabel(key, groupBy))
                        .points(points)
                        .build())
        );
        return result;
    }

    private RevenueDataPoint mapDataPoint(Map<String, Object> row) {
        BigDecimal chargingRevenue = toDecimal(row.get("charging_revenue"));
        BigDecimal bookingRevenue = toDecimal(row.get("booking_revenue"));

        return RevenueDataPoint.builder()
                .periodStart(toInstant(row.get("period")))
                .totalRevenue(toDecimal(row.get("total_revenue")))
                .chargingRevenue(chargingRevenue)
                .bookingRevenue(bookingRevenue)
                .chargingSessions(toLong(row.get("charging_sessions")))
                .bookingCount(toLong(row.get("booking_count")))
                .transactionIds(toStringIds(row.get("transaction_ids")))
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

    private RevenueSummary emptySummary() {
        return RevenueSummary.builder()
                .totalRevenue(BigDecimal.ZERO)
                .chargingRevenue(BigDecimal.ZERO)
                .bookingRevenue(BigDecimal.ZERO)
                .totalChargingSessions(0L)
                .totalBookings(0L)
                .avgChargingRevenuePerSession(BigDecimal.ZERO)
                .avgBookingRevenuePerBooking(BigDecimal.ZERO)
                .uniqueStations(0L)
                .uniqueUsers(0L)
                .build();
    }

    // ── Type helpers ──────────────────────────────────────────────────────────

    private BigDecimal toDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try { return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP); }
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
