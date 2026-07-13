package charg.ing.stations.dto.response;

import java.math.BigDecimal;

/** Почасовой тариф станции: цена зарядки за кВт·ч и брони за минуту в конкретный час (0–23). */
public record HourTariffResponse(
        Integer hour,
        BigDecimal kwCost,
        BigDecimal bookingMinuteCost
) {
}
