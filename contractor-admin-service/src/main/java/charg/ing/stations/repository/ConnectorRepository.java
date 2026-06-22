package charg.ing.stations.repository;

import charg.ing.stations.entity.ConnectorEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ConnectorRepository extends ReactiveCrudRepository<ConnectorEntity, Integer> {
    Flux<ConnectorEntity> findByChargeBoxId(String chargeBoxId);
    Mono<ConnectorEntity> findByChargeBoxIdAndConnectorId(String chargeBoxId, Integer connectorId);
    Flux<ConnectorEntity> findByChargeBoxIdIn(List<String> chargeBoxIds);
}
