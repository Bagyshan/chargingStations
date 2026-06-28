package charg.ing.stations.repository;

import charg.ing.stations.entity.UserFavoriteStation;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserFavoriteStationRepository extends R2dbcRepository<UserFavoriteStation, Long> {

    Flux<UserFavoriteStation> findByKeycloakIdOrderByCreatedAtDesc(String keycloakId);

    Mono<Boolean> existsByKeycloakIdAndChargeBoxId(String keycloakId, String chargeBoxId);

    Mono<Void> deleteByKeycloakIdAndChargeBoxId(String keycloakId, String chargeBoxId);
}
