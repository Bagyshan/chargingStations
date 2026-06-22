package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class RevenueFilter {

    Instant from;
    Instant to;

    @Builder.Default
    Granularity granularity = Granularity.DAY;

    @Builder.Default
    EnergyGroupBy groupBy = EnergyGroupBy.TOTAL;

    // Включать ли выручку от зарядки (transaction.total_sum)
    @Builder.Default
    boolean includeCharging = true;

    // Включать ли выручку от бронирования (booking.total_sum)
    @Builder.Default
    boolean includeBooking = true;

    List<String> stationIds;
    List<String> ownerIds;
    List<Integer> connectorIds;
    List<String> userIds;

    // Статусы для транзакций (зарядка)
    @Builder.Default
    List<String> transactionStatuses = List.of("COMPLETED");

    // Статусы для бронирований
    @Builder.Default
    List<String> bookingStatuses = List.of("COMPLETED");

    Integer timeOfDayFrom;
    Integer timeOfDayTo;
}
