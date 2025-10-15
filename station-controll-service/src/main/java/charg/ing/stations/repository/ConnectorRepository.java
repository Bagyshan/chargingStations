package charg.ing.stations.repository;

import charg.ing.stations.entity.ConnectorEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectorRepository extends JpaRepository<ConnectorEntity, Long> {

    boolean existsByConnectorId(int connectorId);
    Optional<ConnectorEntity> findByConnectorId(int connectorId);

    @Modifying
    @Query("SELECT c.status FROM ConnectorEntity c WHERE c.chargeBoxId = :chargeBoxId AND c.connectorId = :connectorId")
    String findStatusByChargeBoxIdAndConnectorId(@Param("chargeBoxId") String chargeBoxId, @Param("connectorId") Integer connectorId);

    List<ConnectorEntity> findConnectorEntitiesByChargeBoxId(String chargeBoxId);

    ConnectorEntity findByConnectorIdAndChargeBoxId(int connectorId, String chargeBoxId);


    @Modifying
    @Query(value = "UPDATE connector SET version = version + 1 " +
            "WHERE connector_id = :connectorId AND charge_box_id = :chargeBoxId; " +
            "UPDATE charge_box SET version = version + 1 " +
            "WHERE charge_box_id = :chargeBoxId",
            nativeQuery = true)
    int nativeUpdateConnectorVersionAndChargeBoxVersion(@Param("connectorId") int connectorId, @Param("chargeBoxId") String chargeBoxId);

    @Modifying
    @Query("UPDATE ConnectorEntity c SET c.version = c.version + 1 " +
            "WHERE c.connectorId = :connectorId AND c.chargeBox.chargeBoxId = :chargeBoxId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    int incrementConnectorVersion(@Param("connectorId") int connectorId,
                                  @Param("chargeBoxId") String chargeBoxId);


    @Query("SELECT c FROM ConnectorEntity c WHERE c.connectorId = :connectorId AND c.chargeBox.chargeBoxId = :chargeBoxId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    ConnectorEntity findConnectorForUpdate(@Param("connectorId") int connectorId,
                                           @Param("chargeBoxId") String chargeBoxId);

    @Modifying
    @Query("UPDATE ConnectorEntity c SET c.status = :status WHERE c.connectorId = :connectorId AND c.chargeBoxId = :chargeBoxId")
    void updateStatusByConnectorIdAndChargeBoxId(int connectorId, String chargeBoxId, String status);

}