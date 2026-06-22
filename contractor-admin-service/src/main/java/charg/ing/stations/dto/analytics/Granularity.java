package charg.ing.stations.dto.analytics;

public enum Granularity {
    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    private final String sql;

    Granularity(String sql) {
        this.sql = sql;
    }

    public String toSql() {
        return sql;
    }
}
