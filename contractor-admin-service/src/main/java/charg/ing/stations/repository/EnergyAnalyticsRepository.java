package charg.ing.stations.repository;

import charg.ing.stations.dto.analytics.EnergyConsumptionFilter;
import charg.ing.stations.dto.analytics.EnergyGroupBy;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class EnergyAnalyticsRepository {

    private final DatabaseClient db;

    /**
     * Возвращает временные ряды потребления электроэнергии.
     * Каждая строка: (period, group_key, energy_kwh, sessions, avg_duration_minutes, total_revenue)
     */
    public Flux<Map<String, Object>> queryChartData(EnergyConsumptionFilter filter) {
        QuerySpec spec = buildQuery(filter, false);
        return spec.bind(db.sql(spec.sql)).fetch().all();
    }

    /**
     * Возвращает сводную статистику (одна строка).
     */
    public Mono<Map<String, Object>> querySummary(EnergyConsumptionFilter filter) {
        QuerySpec spec = buildQuery(filter, true);
        return spec.bind(db.sql(spec.sql)).fetch().one();
    }

    // ── SQL builder ──────────────────────────────────────────────────────────

    private QuerySpec buildQuery(EnergyConsumptionFilter filter, boolean summaryOnly) {
        String groupKeySql = switch (filter.getGroupBy()) {
            case STATION -> "t.charge_box_id";
            case OWNER   -> "COALESCE(cb.owner_id, 'unknown')";
            case TOTAL   -> "'TOTAL'";
        };

        List<String> conditions = new ArrayList<>();
        List<ParamBinder> binders = new ArrayList<>();

        // date range
        conditions.add("t.start_timestamp >= :from");
        conditions.add("t.start_timestamp <= :to");
        binders.add(s -> s.bind("from", filter.getFrom()));
        binders.add(s -> s.bind("to", filter.getTo()));

        // only transactions with valid energy data unless ACTIVE is explicitly requested
        if (filter.getStatuses() == null || !filter.getStatuses().contains("ACTIVE")) {
            conditions.add("t.stop_timestamp IS NOT NULL");
            conditions.add("t.transaction_value IS NOT NULL");
        }

        // statuses
        if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
            String placeholders = buildInPlaceholders("status", filter.getStatuses().size());
            conditions.add("t.status IN (" + placeholders + ")");
            List<String> statuses = filter.getStatuses();
            for (int i = 0; i < statuses.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("status" + idx, statuses.get(idx)));
            }
        }

        // chargeBoxIds
        if (filter.getChargeBoxIds() != null && !filter.getChargeBoxIds().isEmpty()) {
            String placeholders = buildInPlaceholders("cbId", filter.getChargeBoxIds().size());
            conditions.add("t.charge_box_id IN (" + placeholders + ")");
            List<String> ids = filter.getChargeBoxIds();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("cbId" + idx, ids.get(idx)));
            }
        }

        // ownerIds
        if (filter.getOwnerIds() != null && !filter.getOwnerIds().isEmpty()) {
            String placeholders = buildInPlaceholders("ownerId", filter.getOwnerIds().size());
            conditions.add("cb.owner_id IN (" + placeholders + ")");
            List<String> ids = filter.getOwnerIds();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("ownerId" + idx, ids.get(idx)));
            }
        }

        // connectorIds
        if (filter.getConnectorIds() != null && !filter.getConnectorIds().isEmpty()) {
            String placeholders = buildInPlaceholders("connId", filter.getConnectorIds().size());
            conditions.add("t.connector_id IN (" + placeholders + ")");
            List<Integer> ids = filter.getConnectorIds();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("connId" + idx, ids.get(idx)));
            }
        }

        // userIds
        if (filter.getUserIds() != null && !filter.getUserIds().isEmpty()) {
            String placeholders = buildInPlaceholders("userId", filter.getUserIds().size());
            conditions.add("t.user_id IN (" + placeholders + ")");
            List<String> ids = filter.getUserIds();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("userId" + idx, ids.get(idx)));
            }
        }

        // time-of-day filter (UTC hour)
        if (filter.getTimeOfDayFrom() != null) {
            conditions.add("EXTRACT(HOUR FROM t.start_timestamp AT TIME ZONE 'UTC') >= :todFrom");
            binders.add(s -> s.bind("todFrom", filter.getTimeOfDayFrom()));
        }
        if (filter.getTimeOfDayTo() != null) {
            conditions.add("EXTRACT(HOUR FROM t.start_timestamp AT TIME ZONE 'UTC') <= :todTo");
            binders.add(s -> s.bind("todTo", filter.getTimeOfDayTo()));
        }

        String whereClause = conditions.isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", conditions);

        String sql;
        if (summaryOnly) {
            sql = """
                    SELECT
                        COALESCE(SUM(t.transaction_value), 0) / 1000.0                                                        AS total_energy_kwh,
                        COUNT(*)                                                                                               AS total_sessions,
                        COALESCE(AVG(EXTRACT(EPOCH FROM (t.stop_timestamp - t.start_timestamp)) / 60.0), 0)                   AS avg_duration_minutes,
                        COALESCE(SUM(t.total_sum), 0)                                                                         AS total_revenue,
                        COUNT(DISTINCT t.charge_box_id)                                                                       AS unique_stations,
                        COUNT(DISTINCT t.user_id)                                                                             AS unique_users
                    FROM transaction t
                    LEFT JOIN charge_box cb ON t.charge_box_id = cb.charge_box_id
                    %s
                    """.formatted(whereClause);
        } else {
            String granSql = filter.getGranularity().toSql();
            sql = """
                    SELECT
                        date_trunc('%s', t.start_timestamp) AT TIME ZONE 'UTC'                                                 AS period,
                        %s                                                                                                     AS group_key,
                        COALESCE(SUM(t.transaction_value), 0) / 1000.0                                                        AS energy_kwh,
                        COUNT(*)                                                                                               AS sessions,
                        COALESCE(AVG(EXTRACT(EPOCH FROM (t.stop_timestamp - t.start_timestamp)) / 60.0), 0)                   AS avg_duration_minutes,
                        COALESCE(SUM(t.total_sum), 0)                                                                         AS total_revenue,
                        string_agg(CAST(t.transaction_id AS TEXT), ',' ORDER BY t.transaction_id)                             AS transaction_ids
                    FROM transaction t
                    LEFT JOIN charge_box cb ON t.charge_box_id = cb.charge_box_id
                    %s
                    GROUP BY period, group_key
                    ORDER BY period, group_key
                    """.formatted(granSql, groupKeySql, whereClause);
        }

        return new QuerySpec(sql, binders);
    }

    private static String buildInPlaceholders(String prefix, int count) {
        List<String> placeholders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            placeholders.add(":" + prefix + i);
        }
        return String.join(", ", placeholders);
    }

    // ── Inner helpers ─────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ParamBinder {
        DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec);
    }

    private record QuerySpec(String sql, List<ParamBinder> binders) {
        DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec) {
            for (ParamBinder binder : binders) {
                spec = binder.bind(spec);
            }
            return spec;
        }
    }
}
