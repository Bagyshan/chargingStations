package charg.ing.stations.repository;

import charg.ing.stations.dto.StationHourlyTariffEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChargeBoxBatchRepository {

    private final DatabaseClient databaseClient;

    public Mono<Void> batchUpdateTariffs(List<StationHourlyTariffEvent> tariffs) {
        return Flux.fromIterable(tariffs)
                .flatMap(tariff ->
                        databaseClient.sql("""
                                UPDATE charge_box
                                SET kw_cost = :kwCost, booking_minute_cost = :bookingMinuteCost
                                WHERE charge_box_id = :stationId
                                """)
                                .bind("kwCost", tariff.getKwCost())
                                .bind("bookingMinuteCost", tariff.getBookingMinuteCost())
                                .bind("stationId", tariff.getStationId())
                                .fetch()
                                .rowsUpdated()
                )
                .then();
    }
}
