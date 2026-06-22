package charg.ing.stations.service;

import charg.ing.stations.dto.StationHourlyTariffEvent;
import charg.ing.stations.repository.ChargeBoxBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChargeBoxTariffService {

    private final ChargeBoxBatchRepository repository;

    public Mono<Void> updateTariffs(List<StationHourlyTariffEvent> tariffs) {
        return repository.batchUpdateTariffs(tariffs);
    }
}