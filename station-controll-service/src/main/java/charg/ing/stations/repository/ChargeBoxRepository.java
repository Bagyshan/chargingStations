package charg.ing.stations.repository;

import charg.ing.stations.entity.ChargeBoxEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ChargeBoxRepository extends JpaRepository<ChargeBoxEntity, Long> {


    @Modifying
    @Query("UPDATE ChargeBoxEntity c SET c.version = c.version + 1 " +
            "WHERE c.id = :id AND c.version = :originalVersion")
    void incrementVersion(@Param("id") String id, @Param("originalVersion") Long originalVersion);


    @Modifying
    @Query("UPDATE ChargeBoxEntity c SET c.version = c.version + 1 " +
            "WHERE c.chargeBoxId = :chargeBoxId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    int incrementChargeBoxVersion(@Param("chargeBoxId") String chargeBoxId);


    @Query("SELECT cb FROM ChargeBoxEntity cb WHERE cb.chargeBoxId = :chargeBoxId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    ChargeBoxEntity findChargeBoxForUpdate(@Param("chargeBoxId") String chargeBoxId);

    boolean existsByChargeBoxId(String chargeBoxId);
    ChargeBoxEntity findByChargeBoxId(String chargeBoxId);

}