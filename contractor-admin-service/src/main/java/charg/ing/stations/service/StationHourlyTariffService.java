package charg.ing.stations.service;

import charg.ing.stations.dto.request.HourTariffRequest;
import charg.ing.stations.dto.request.UpdateStationTariffsRequest;
import charg.ing.stations.entity.StationHourlyTariffEntity;
import charg.ing.stations.repository.StationHourlyTariffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationHourlyTariffService {

    private final StationHourlyTariffRepository repository;

    public Mono<Void> saveStationTariffs(
            String stationId,
            UpdateStationTariffsRequest request
    ) {

        validateHours(request);

        return repository.findByStationId(stationId)
                .collectList()
                .flatMap(existingTariffs -> {

                    if (existingTariffs.isEmpty()) {

                        return createTariffs(
                                stationId,
                                request
                        );
                    }

                    return updateTariffs(
                            existingTariffs,
                            request
                    );
                });
    }

    private Mono<Void> createTariffs(
            String stationId,
            UpdateStationTariffsRequest request
    ) {

        return repository.saveAll(
                        request.tariffs()
                                .stream()
                                .map(hourTariff ->
                                        new StationHourlyTariffEntity(
                                                null,
                                                stationId,
                                                hourTariff.hour(),
                                                hourTariff.kwCost(),
                                                hourTariff.bookingMinuteCost(),
                                                Instant.now(),
                                                Instant.now()
                                        )
                                )
                                .toList()
                )
                .then();
    }

    private Mono<Void> updateTariffs(
            java.util.List<StationHourlyTariffEntity> existingTariffs,
            UpdateStationTariffsRequest request
    ) {

        Map<Integer, StationHourlyTariffEntity> tariffMap =
                existingTariffs.stream()
                        .collect(Collectors.toMap(
                                StationHourlyTariffEntity::getHour,
                                Function.identity()
                        ));

        return Flux.fromIterable(request.tariffs())
                .flatMap(dto -> {

                    StationHourlyTariffEntity entity =
                            tariffMap.get(dto.hour());

                    if (entity == null) {
                        return Mono.empty();
                    }

                    entity.setKwCost(dto.kwCost());
                    entity.setBookingMinuteCost(
                            dto.bookingMinuteCost()
                    );
                    entity.setUpdatedAt(Instant.now());

                    return repository.save(entity);
                })
                .then();
    }

    private void validateHours(
            UpdateStationTariffsRequest request
    ) {

        if (request.tariffs().size() != 24) {
            throw new IllegalArgumentException(
                    "Exactly 24 tariffs are required"
            );
        }

        long uniqueHours =
                request.tariffs()
                        .stream()
                        .map(HourTariffRequest::hour)
                        .distinct()
                        .count();

        if (uniqueHours != 24) {
            throw new IllegalArgumentException(
                    "Hours must be unique from 0 to 23"
            );
        }
    }
}