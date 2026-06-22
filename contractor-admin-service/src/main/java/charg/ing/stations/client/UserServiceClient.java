package charg.ing.stations.client;

import charg.ing.stations.dto.external.UserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class UserServiceClient {

    private final WebClient webClient;

    public UserServiceClient(@Qualifier("userServiceWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Flux<UserDto> getAllUsers(String bearerToken) {
        log.debug("Fetching all users from user-service");
        return webClient.get()
                .uri("/api/v1/users/all")
                .header("Authorization", bearerToken)
                .retrieve()
                .bodyToFlux(UserDto.class)
                .doOnError(e -> log.error("Failed to fetch users: {}", e.getMessage()));
    }
}
