package charg.ing.stations.enums;

/**
 * Типизированная причина, по которой станция/коннектор недоступны для брони или зарядки.
 * Возвращается из {@code StationAvailabilityService} и мапится на HTTP-статус / текст ошибки саги.
 */
public enum UnavailabilityReason {
    STATION_NOT_FOUND,
    CONNECTOR_NOT_FOUND,
    OUT_OF_SERVICE,
    OFFLINE,
    NOT_OPERATIONAL,
    RESERVED_BY_OTHER,
    ALREADY_RESERVED
}
