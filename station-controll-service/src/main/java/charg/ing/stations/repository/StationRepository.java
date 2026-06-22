package charg.ing.stations.repository;

import charg.ing.stations.entity.ChargeBoxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StationRepository extends JpaRepository<ChargeBoxEntity, String> {
    // Можно добавить кастомные методы запросов если нужно


    Optional<ChargeBoxEntity> findByChargeBoxId(String chargeBoxId);
}