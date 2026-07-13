package charg.ing.stations.repository;

import charg.ing.stations.dto.analytics.EnergyGroupBy;
import charg.ing.stations.dto.analytics.RevenueFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RevenueAnalyticsRepository {

    private final DatabaseClient db;

    public Flux<Map<String, Object>> queryChartData(RevenueFilter filter) {
        QuerySpec spec = buildQuery(filter, false);
        return spec.bind(db.sql(spec.sql)).fetch().all();
    }

    public Mono<Map<String, Object>> querySummary(RevenueFilter filter) {
        QuerySpec spec = buildQuery(filter, true);
        return spec.bind(db.sql(spec.sql)).fetch().one();
    }

    // ── SQL builder ──────────────────────────────────────────────────────────

    private QuerySpec buildQuery(RevenueFilter filter, boolean summaryOnly) {
        List<String> subqueries = new ArrayList<>();
        List<ParamBinder> binders = new ArrayList<>();

        String groupKeySql = groupKeyExpression(filter.getGroupBy());
        String granSql = filter.getGranularity().toSql();

        if (filter.isIncludeCharging()) {
            buildChargingSubquery(filter, subqueries, binders, groupKeySql, granSql);
        }
        if (filter.isIncludeBooking()) {
            buildBookingSubquery(filter, subqueries, binders, groupKeySql, granSql);
        }

        if (subqueries.isEmpty()) {
            // ничего не включено — вернуть пустой результат
            String empty = summaryOnly
                    ? "SELECT 0::decimal AS total_charging_revenue, 0::decimal AS total_booking_revenue, 0 AS total_charging_sessions, 0 AS total_bookings, 0 AS unique_stations, 0 AS unique_users"
                    : "SELECT NULL::timestamptz AS period, 'TOTAL' AS group_key, 0::decimal AS charging_revenue, 0::decimal AS booking_revenue, 0 AS charging_sessions, 0 AS booking_count, '' AS txn_ids, '' AS bkg_ids WHERE false";
            return new QuerySpec(empty, binders);
        }

        String unionSql = String.join("\nUNION ALL\n", subqueries);

        String sql;
        if (summaryOnly) {
            sql = """
                    SELECT
                        COALESCE(SUM(charging_revenue), 0)   AS total_charging_revenue,
                        COALESCE(SUM(booking_revenue), 0)    AS total_booking_revenue,
                        COALESCE(SUM(charging_sessions), 0)  AS total_charging_sessions,
                        COALESCE(SUM(booking_count), 0)      AS total_bookings,
                        COUNT(DISTINCT station_id)            AS unique_stations,
                        COUNT(DISTINCT user_id)               AS unique_users
                    FROM (%s) combined
                    """.formatted(unionSql);
        } else {
            sql = """
                    SELECT
                        period,
                        group_key,
                        COALESCE(SUM(charging_revenue), 0)                                     AS charging_revenue,
                        COALESCE(SUM(booking_revenue), 0)                                      AS booking_revenue,
                        COALESCE(SUM(charging_revenue + booking_revenue), 0)                   AS total_revenue,
                        COALESCE(SUM(charging_sessions), 0)                                    AS charging_sessions,
                        COALESCE(SUM(booking_count), 0)                                        AS booking_count,
                        string_agg(NULLIF(txn_ids, ''), ',')                                   AS transaction_ids,
                        string_agg(NULLIF(bkg_ids, ''), ',')                                   AS booking_ids
                    FROM (%s) combined
                    GROUP BY period, group_key
                    ORDER BY period, group_key
                    """.formatted(unionSql);
        }

        return new QuerySpec(sql, binders);
    }

    // ── Charging subquery ────────────────────────────────────────────────────

    private void buildChargingSubquery(RevenueFilter filter,
                                       List<String> subqueries,
                                       List<ParamBinder> binders,
                                       String groupKeySql,
                                       String granSql) {
        List<String> conditions = new ArrayList<>();

        conditions.add("t.start_timestamp >= :t_from");
        conditions.add("t.start_timestamp <= :t_to");
        // Раньше требовалось t.total_sum IS NOT NULL — из-за этого несведённые оплаты
        // (без расчёта через O!Dengi) выпадали, а доход был нулевым. Теперь берём сессии
        // с валидной энергией, а доход при отсутствии total_sum оцениваем как энергия × тариф.
        conditions.add("t.transaction_value IS NOT NULL");
        binders.add(s -> s.bind("t_from", filter.getFrom()));
        binders.add(s -> s.bind("t_to", filter.getTo()));

        if (filter.getTransactionStatuses() != null && !filter.getTransactionStatuses().isEmpty()) {
            String ph = buildInPlaceholders("t_st", filter.getTransactionStatuses().size());
            conditions.add("t.status IN (" + ph + ")");
            List<String> statuses = filter.getTransactionStatuses();
            for (int i = 0; i < statuses.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("t_st" + idx, statuses.get(idx)));
            }
        }

        applyStationFilter(filter, conditions, binders, "t.charge_box_id", "t_cb");
        applyOwnerFilter(filter, conditions, binders, "t_own");
        applyConnectorFilter(filter, conditions, binders, "t.connector_id", "t_conn");
        applyUserFilter(filter, conditions, binders, "t.user_id", "t_usr");
        applyTimeOfDayFilter(filter, conditions, binders, "t.start_timestamp", "t_tod");

        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);

        subqueries.add("""
                SELECT
                    date_trunc('%s', t.start_timestamp) AT TIME ZONE 'UTC'  AS period,
                    %s                                                        AS group_key,
                    COALESCE(NULLIF(t.total_sum, 0), (t.transaction_value / 1000.0) * COALESCE(t.price_per_kwh, cb.kw_cost, 0), 0) AS charging_revenue,
                    0::decimal                                                AS booking_revenue,
                    1                                                         AS charging_sessions,
                    0                                                         AS booking_count,
                    t.charge_box_id                                           AS station_id,
                    CAST(t.user_id AS TEXT)                                   AS user_id,
                    CAST(t.transaction_id AS TEXT)                            AS txn_ids,
                    ''                                                        AS bkg_ids
                FROM transaction t
                LEFT JOIN charge_box cb ON t.charge_box_id = cb.charge_box_id
                %s
                """.formatted(granSql, groupKeySql, where));
    }

    // ── Booking subquery ─────────────────────────────────────────────────────

    private void buildBookingSubquery(RevenueFilter filter,
                                      List<String> subqueries,
                                      List<ParamBinder> binders,
                                      String groupKeySql,
                                      String granSql) {
        List<String> conditions = new ArrayList<>();

        conditions.add("b.started_at >= :b_from");
        conditions.add("b.started_at <= :b_to");
        // Доход брони при отсутствии total_sum оцениваем как минуты × тариф за минуту.
        conditions.add("b.total_minutes IS NOT NULL");
        binders.add(s -> s.bind("b_from", filter.getFrom()));
        binders.add(s -> s.bind("b_to", filter.getTo()));

        if (filter.getBookingStatuses() != null && !filter.getBookingStatuses().isEmpty()) {
            String ph = buildInPlaceholders("b_st", filter.getBookingStatuses().size());
            conditions.add("b.status IN (" + ph + ")");
            List<String> statuses = filter.getBookingStatuses();
            for (int i = 0; i < statuses.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("b_st" + idx, statuses.get(idx)));
            }
        }

        applyStationFilter(filter, conditions, binders, "b.station_id", "b_cb");
        applyOwnerFilter(filter, conditions, binders, "b_own");
        applyConnectorFilter(filter, conditions, binders, "b.connector_id", "b_conn");
        applyUserFilter(filter, conditions, binders, "CAST(b.user_id AS TEXT)", "b_usr");
        applyTimeOfDayFilter(filter, conditions, binders, "b.started_at", "b_tod");

        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);

        // booking таблица использует station_id, для groupBy STATION нужно b.station_id
        String bGroupKey = groupKeySql
                .replace("t.charge_box_id", "b.station_id");

        subqueries.add("""
                SELECT
                    date_trunc('%s', b.started_at) AT TIME ZONE 'UTC'        AS period,
                    %s                                                        AS group_key,
                    0::decimal                                                AS charging_revenue,
                    COALESCE(NULLIF(b.total_sum, 0), b.total_minutes * b.price_per_minute, 0) AS booking_revenue,
                    0                                                         AS charging_sessions,
                    1                                                         AS booking_count,
                    b.station_id                                              AS station_id,
                    CAST(b.user_id AS TEXT)                                   AS user_id,
                    ''                                                        AS txn_ids,
                    CAST(b.booking_id AS TEXT)                                AS bkg_ids
                FROM booking b
                LEFT JOIN charge_box cb ON b.station_id = cb.charge_box_id
                %s
                """.formatted(granSql, bGroupKey, where));
    }

    // ── Shared filter helpers ─────────────────────────────────────────────────

    private void applyStationFilter(RevenueFilter f, List<String> cond,
                                    List<ParamBinder> binders, String col, String prefix) {
        if (f.getStationIds() == null || f.getStationIds().isEmpty()) return;
        String ph = buildInPlaceholders(prefix, f.getStationIds().size());
        cond.add(col + " IN (" + ph + ")");
        List<String> ids = f.getStationIds();
        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            binders.add(s -> s.bind(prefix + idx, ids.get(idx)));
        }
    }

    private void applyOwnerFilter(RevenueFilter f, List<String> cond,
                                  List<ParamBinder> binders, String prefix) {
        if (f.getOwnerIds() == null || f.getOwnerIds().isEmpty()) return;
        String ph = buildInPlaceholders(prefix, f.getOwnerIds().size());
        cond.add("cb.owner_id IN (" + ph + ")");
        List<String> ids = f.getOwnerIds();
        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            binders.add(s -> s.bind(prefix + idx, ids.get(idx)));
        }
    }

    private void applyConnectorFilter(RevenueFilter f, List<String> cond,
                                      List<ParamBinder> binders, String col, String prefix) {
        if (f.getConnectorIds() == null || f.getConnectorIds().isEmpty()) return;
        String ph = buildInPlaceholders(prefix, f.getConnectorIds().size());
        cond.add(col + " IN (" + ph + ")");
        List<Integer> ids = f.getConnectorIds();
        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            binders.add(s -> s.bind(prefix + idx, ids.get(idx)));
        }
    }

    private void applyUserFilter(RevenueFilter f, List<String> cond,
                                 List<ParamBinder> binders, String col, String prefix) {
        if (f.getUserIds() == null || f.getUserIds().isEmpty()) return;
        String ph = buildInPlaceholders(prefix, f.getUserIds().size());
        cond.add(col + " IN (" + ph + ")");
        List<String> ids = f.getUserIds();
        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            binders.add(s -> s.bind(prefix + idx, ids.get(idx)));
        }
    }

    private void applyTimeOfDayFilter(RevenueFilter f, List<String> cond,
                                      List<ParamBinder> binders, String tsCol, String prefix) {
        if (f.getTimeOfDayFrom() != null) {
            cond.add("EXTRACT(HOUR FROM " + tsCol + " AT TIME ZONE 'UTC') >= :" + prefix + "From");
            binders.add(s -> s.bind(prefix + "From", f.getTimeOfDayFrom()));
        }
        if (f.getTimeOfDayTo() != null) {
            cond.add("EXTRACT(HOUR FROM " + tsCol + " AT TIME ZONE 'UTC') <= :" + prefix + "To");
            binders.add(s -> s.bind(prefix + "To", f.getTimeOfDayTo()));
        }
    }

    private String groupKeyExpression(EnergyGroupBy groupBy) {
        return switch (groupBy) {
            case STATION -> "t.charge_box_id";   // переопределяется в booking subquery
            case OWNER   -> "COALESCE(cb.owner_id, 'unknown')";
            case TOTAL   -> "'TOTAL'";
        };
    }

    private static String buildInPlaceholders(String prefix, int count) {
        List<String> ph = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ph.add(":" + prefix + i);
        return String.join(", ", ph);
    }

    @FunctionalInterface
    private interface ParamBinder {
        DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec);
    }

    private record QuerySpec(String sql, List<ParamBinder> binders) {
        DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec) {
            for (ParamBinder b : binders) spec = b.bind(spec);
            return spec;
        }
    }
}
