package charg.ing.stations.controller;

import charg.ing.stations.client.StationControllClient;
import charg.ing.stations.client.UserServiceClient;
import charg.ing.stations.dto.external.AddressDto;
import charg.ing.stations.dto.external.ConnectorTypeDto;
import charg.ing.stations.dto.external.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
@Tag(name = "External Data", description = "Data aggregated from other microservices (ADMIN, SPECIALIST, CONTRACTOR)")
public class ExternalDataController {

    private final StationControllClient stationControllClient;
    private final UserServiceClient userServiceClient;

    @GetMapping("/addresses")
    @Operation(summary = "Get all station addresses from station-controll-service")
    public Flux<AddressDto> getAddresses(@AuthenticationPrincipal Jwt jwt) {
        return stationControllClient.getAllAddresses("Bearer " + jwt.getTokenValue());
    }

    @GetMapping("/connector-types")
    @Operation(summary = "Get all connector types from station-controll-service")
    public Flux<ConnectorTypeDto> getConnectorTypes(@AuthenticationPrincipal Jwt jwt) {
        return stationControllClient.getAllConnectorTypes("Bearer " + jwt.getTokenValue());
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users from user-service")
    public Flux<UserDto> getUsers(@AuthenticationPrincipal Jwt jwt) {
        return userServiceClient.getAllUsers("Bearer " + jwt.getTokenValue());
    }
}
