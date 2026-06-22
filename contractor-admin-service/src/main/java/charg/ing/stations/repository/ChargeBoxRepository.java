package charg.ing.stations.repository;

import charg.ing.stations.entity.ChargeBoxEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChargeBoxRepository extends ReactiveCrudRepository<ChargeBoxEntity, Integer> {
    Mono<ChargeBoxEntity> findByChargeBoxId(String chargeBoxId);
    Flux<ChargeBoxEntity> findByOwnerId(String ownerId);
}
