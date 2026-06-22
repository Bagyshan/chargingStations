package charg.ing.stations.service;

import charg.ing.stations.dto.event.StationHourlyTariffEvent;
import charg.ing.stations.repository.ChargeBoxBatchRepository;
import charg.ing.stations.repository.ChargeBoxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StationTariffUpdateService {

    private final ChargeBoxBatchRepository batchRepository;

    public void updateTariffs(
            List<StationHourlyTariffEvent> tariffs
    ) {

        batchRepository.batchUpdateTariffs(
                tariffs
        );
    }
}