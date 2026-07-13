package charg.ing.stations.controller;

import charg.ing.stations.dto.request.UpdateStationTariffsRequest;
import charg.ing.stations.dto.response.HourTariffResponse;
import charg.ing.stations.service.StationHourlyTariffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationHourlyTariffController {

    private final StationHourlyTariffService service;

    @GetMapping("/{stationId}/hourly-tariffs")
    public Flux<HourTariffResponse> getTariffs(@PathVariable String stationId) {
        return service.getStationTariffs(stationId);
    }

    @PutMapping("/{stationId}/hourly-tariffs")
    public Mono<ResponseEntity<Void>> saveTariffs(
            @PathVariable String stationId,
            @Valid @RequestBody UpdateStationTariffsRequest request
    ) {

        return service.saveStationTariffs(
                        stationId,
                        request
                )
                .thenReturn(ResponseEntity.ok().build());
    }
}