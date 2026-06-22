package charg.ing.stations.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class EnergyConsumptionFilter {

    Instant from;
    Instant to;

    @Builder.Default
    Granularity granularity = Granularity.DAY;

    @Builder.Default
    EnergyGroupBy groupBy = EnergyGroupBy.TOTAL;

    // null = все станции
    List<String> chargeBoxIds;

    // null = все контрагенты
    List<String> ownerIds;

    // null = все коннекторы
    List<Integer> connectorIds;

    // null = все пользователи
    List<String> userIds;

    // По умолчанию только завершённые транзакции
    @Builder.Default
    List<String> statuses = List.of("COMPLETED");

    // Фильтрация по часу суток UTC (0–23), null = без ограничений
    Integer timeOfDayFrom;
    Integer timeOfDayTo;
}
