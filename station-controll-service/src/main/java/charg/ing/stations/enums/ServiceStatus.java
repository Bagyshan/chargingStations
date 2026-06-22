package charg.ing.stations.enums;

/**
 * Административный (operator-controlled) статус станции — отдельно от operational-статуса
 * коннектора, который приходит со станции по OCPP StatusNotification.
 *
 * <ul>
 *   <li>{@link #IN_SERVICE} — станция в работе, доступна для брони и зарядки;</li>
 *   <li>{@link #OUT_OF_SERVICE} — выведена из эксплуатации оператором (нельзя бронировать/заряжать);</li>
 *   <li>{@link #MAINTENANCE} — на обслуживании (тоже недоступна).</li>
 * </ul>
 */
public enum ServiceStatus {
    IN_SERVICE,
    OUT_OF_SERVICE,
    MAINTENANCE
}
