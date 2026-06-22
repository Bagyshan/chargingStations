package charg.ing.stations.repository;

import charg.ing.stations.entity.StationHourlyTariffEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StationHourlyTariffRepository
        extends ReactiveCrudRepository<StationHourlyTariffEntity, Long> {

    Flux<StationHourlyTariffEntity> findByStationId(String chargeBoxId);

    Mono<StationHourlyTariffEntity> findByStationIdAndHour(
            String chargeBoxId,
            Integer hourOfDay
    );

    @Query("""
        SELECT *
        FROM station_hourly_tariffs
        WHERE hour = :hour
    """)
    Flux<StationHourlyTariffEntity> findAllByHour(Integer hour);
}