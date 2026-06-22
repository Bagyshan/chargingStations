package charg.ing.stations.service;

import charg.ing.stations.dto.analytics.*;
import charg.ing.stations.repository.EnergyAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnergyAnalyticsService {

    private final EnergyAnalyticsRepository repository;

    public Mono<EnergyConsumptionResponse> getEnergyConsumption(EnergyConsumptionFilter filter) {
        log.debug("Energy analytics request: from={} to={} granularity={} groupBy={}",
                filter.getFrom(), filter.getTo(), filter.getGranularity(), filter.getGroupBy());

        Mono<EnergyConsumptionSummary> summaryMono = repository.querySummary(filter)
                .map(this::mapSummary)
                .defaultIfEmpty(emptySummary());

        Mono<List<EnergySeries>> seriesMono = repository.queryChartData(filter)
                .collectList()
                .map(rows -> buildSeries(rows, filter.getGroupBy()));

        return Mono.zip(summaryMono, seriesMono)
                .map(tuple -> EnergyConsumptionResponse.builder()
                        .from(filter.getFrom())
                        .to(filter.getTo())
                        .granularity(filter.getGranularity())
                        .groupBy(filter.getGroupBy())
                        .summary(tuple.getT1())
                        .series(tuple.getT2())
                        .build());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private EnergyConsumptionSummary mapSummary(Map<String, Object> row) {
        long sessions = toLong(row.get("total_sessions"));
        BigDecimal totalEnergy = toDecimal(row.get("total_energy_kwh"));
        BigDecimal avgEnergy = sessions > 0
                ? totalEnergy.divide(BigDecimal.valueOf(sessions), 3, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return EnergyConsumptionSummary.builder()
                .totalEnergyKwh(totalEnergy)
                .totalSessions(sessions)
                .avgEnergyPerSessionKwh(avgEnergy)
                .avgSessionDurationMinutes(toDecimal(row.get("avg_duration_minutes")))
                .totalRevenue(toDecimal(row.get("total_revenue")))
                .uniqueStations(toLong(row.get("unique_stations")))
                .uniqueUsers(toLong(row.get("unique_users")))
                .build();
    }

    private List<EnergySeries> buildSeries(List<Map<String, Object>> rows, EnergyGroupBy groupBy) {
        // Группируем строки по group_key → каждый ключ = одна серия
        Map<String, List<EnergyDataPoint>> seriesMap = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String key = Objects.toString(row.get("group_key"), "TOTAL");
            seriesMap.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(mapDataPoint(row));
        }

        List<EnergySeries> result = new ArrayList<>(seriesMap.size());
        seriesMap.forEach((key, points) ->
                result.add(EnergySeries.builder()
                        .groupKey(key)
                        .label(buildLabel(key, groupBy))
                        .points(points)
                        .build())
        );
        return result;
    }

    private EnergyDataPoint mapDataPoint(Map<String, Object> row) {
        return EnergyDataPoint.builder()
                .periodStart(toInstant(row.get("period")))
                .energyKwh(toDecimal(row.get("energy_kwh")))
                .sessions(toLong(row.get("sessions")))
                .avgDurationMinutes(toDecimal(row.get("avg_duration_minutes")))
                .revenue(toDecimal(row.get("total_revenue")))
                .transactionIds(toTransactionIds(row.get("transaction_ids")))
                .build();
    }

    private String buildLabel(String groupKey, EnergyGroupBy groupBy) {
        return switch (groupBy) {
            case TOTAL   -> "Total";
            case STATION -> "Station " + groupKey;
            case OWNER   -> "Contractor " + groupKey;
        };
    }

    private EnergyConsumptionSummary emptySummary() {
        return EnergyConsumptionSummary.builder()
                .totalEnergyKwh(BigDecimal.ZERO)
                .totalSessions(0L)
                .avgEnergyPerSessionKwh(BigDecimal.ZERO)
                .avgSessionDurationMinutes(BigDecimal.ZERO)
                .totalRevenue(BigDecimal.ZERO)
                .uniqueStations(0L)
                .uniqueUsers(0L)
                .build();
    }

    // ── Type conversion helpers ───────────────────────────────────────────────

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

    private List<Long> toTransactionIds(Object value) {
        if (value == null) return List.of();
        // string_agg возвращает строку вида "78,79,81"
        String csv = value.toString().trim();
        if (csv.isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            try { ids.add(Long.parseLong(part.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        // R2DBC может вернуть LocalDateTime или OffsetDateTime
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) return ldt.toInstant(java.time.ZoneOffset.UTC);
        return null;
    }
}
