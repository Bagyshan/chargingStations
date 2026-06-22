package charg.ing.stations.repository;

import charg.ing.stations.dto.event.StationHourlyTariffEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChargeBoxBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchUpdateTariffs(
            List<StationHourlyTariffEvent> tariffs
    ) {

        jdbcTemplate.batchUpdate(
                """
                UPDATE charge_box
                SET
                    kw_cost = ?,
                    booking_minute_cost = ?
                WHERE charge_box_id = ?
                """,
                tariffs,
                tariffs.size(),
                (ps, tariff) -> {

                    ps.setBigDecimal(
                            1,
                            tariff.getKwCost()
                    );

                    ps.setBigDecimal(
                            2,
                            tariff.getBookingMinuteCost()
                    );

                    ps.setString(
                            3,
                            tariff.getStationId()
                    );
                }
        );
    }
}