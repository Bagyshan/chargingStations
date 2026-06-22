package charg.ing.stations.repository;

import charg.ing.stations.dto.analytics.BookingConsumptionFilter;
import charg.ing.stations.dto.analytics.EnergyGroupBy;
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
public class BookingAnalyticsRepository {

    private final DatabaseClient db;

    public Flux<Map<String, Object>> queryChartData(BookingConsumptionFilter filter) {
        QuerySpec spec = buildQuery(filter, false);
        return spec.bind(db.sql(spec.sql)).fetch().all();
    }

    public Mono<Map<String, Object>> querySummary(BookingConsumptionFilter filter) {
        QuerySpec spec = buildQuery(filter, true);
        return spec.bind(db.sql(spec.sql)).fetch().one();
    }

    // ── SQL builder ──────────────────────────────────────────────────────────

    private QuerySpec buildQuery(BookingConsumptionFilter filter, boolean summaryOnly) {
        String groupKeySql = switch (filter.getGroupBy()) {
            case STATION -> "b.station_id";
            case OWNER   -> "COALESCE(cb.owner_id, 'unknown')";
            case TOTAL   -> "'TOTAL'";
        };

        List<String> conditions = new ArrayList<>();
        List<ParamBinder> binders = new ArrayList<>();

        conditions.add("b.started_at >= :from");
        conditions.add("b.started_at <= :to");
        binders.add(s -> s.bind("from", filter.getFrom()));
        binders.add(s -> s.bind("to", filter.getTo()));

        // statuses
        if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
            String placeholders = buildInPlaceholders("status", filter.getStatuses().size());
            conditions.add("b.status IN (" + placeholders + ")");
            List<String> statuses = filter.getStatuses();
            for (int i = 0; i < statuses.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("status" + idx, statuses.get(idx)));
            }
        }

        // stationIds
        if (filter.getStationIds() != null && !filter.getStationIds().isEmpty()) {
            String placeholders = buildInPlaceholders("stId", filter.getStationIds().size());
            conditions.add("b.station_id IN (" + placeholders + ")");
            List<String> ids = filter.getStationIds();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("stId" + idx, ids.get(idx)));
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
            conditions.add("b.connector_id IN (" + placeholders + ")");
            List<Integer> ids = filter.getConnectorIds();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("connId" + idx, ids.get(idx)));
            }
        }

        // userIds (UUID cast to text for comparison)
        if (filter.getUserIds() != null && !filter.getUserIds().isEmpty()) {
            String placeholders = buildInPlaceholders("userId", filter.getUserIds().size());
            conditions.add("CAST(b.user_id AS TEXT) IN (" + placeholders + ")");
            List<String> ids = filter.getUserIds();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                binders.add(s -> s.bind("userId" + idx, ids.get(idx)));
            }
        }

        // time-of-day
        if (filter.getTimeOfDayFrom() != null) {
            conditions.add("EXTRACT(HOUR FROM b.started_at AT TIME ZONE 'UTC') >= :todFrom");
            binders.add(s -> s.bind("todFrom", filter.getTimeOfDayFrom()));
        }
        if (filter.getTimeOfDayTo() != null) {
            conditions.add("EXTRACT(HOUR FROM b.started_at AT TIME ZONE 'UTC') <= :todTo");
            binders.add(s -> s.bind("todTo", filter.getTimeOfDayTo()));
        }

        String whereClause = conditions.isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", conditions);

        String sql;
        if (summaryOnly) {
            sql = """
                    SELECT
                        COUNT(*)                                                                AS total_bookings,
                        COUNT(*) FILTER (WHERE b.status = 'COMPLETED')                         AS completed_bookings,
                        COUNT(*) FILTER (WHERE b.status = 'CANCELLED')                         AS cancelled_bookings,
                        COALESCE(SUM(b.total_minutes), 0)                                      AS total_minutes,
                        COALESCE(AVG(b.total_minutes), 0)                                      AS avg_duration_minutes,
                        COALESCE(SUM(b.total_sum), 0)                                          AS total_revenue,
                        COUNT(DISTINCT b.station_id)                                           AS unique_stations,
                        COUNT(DISTINCT b.user_id)                                              AS unique_users
                    FROM booking b
                    LEFT JOIN charge_box cb ON b.station_id = cb.charge_box_id
                    %s
                    """.formatted(whereClause);
        } else {
            String granSql = filter.getGranularity().toSql();
            sql = """
                    SELECT
                        date_trunc('%s', b.started_at) AT TIME ZONE 'UTC'                      AS period,
                        %s                                                                     AS group_key,
                        COUNT(*)                                                               AS bookings,
                        COALESCE(SUM(b.total_minutes), 0)                                      AS total_minutes,
                        COALESCE(AVG(b.total_minutes), 0)                                      AS avg_duration_minutes,
                        COALESCE(SUM(b.total_sum), 0)                                          AS total_revenue,
                        string_agg(CAST(b.booking_id AS TEXT), ',' ORDER BY b.started_at)     AS booking_ids
                    FROM booking b
                    LEFT JOIN charge_box cb ON b.station_id = cb.charge_box_id
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
