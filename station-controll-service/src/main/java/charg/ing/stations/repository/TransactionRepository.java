package charg.ing.stations.repository;

import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    Optional<TransactionEntity> findByTransactionId(Integer transactionId);
    List<TransactionEntity> findByChargeBoxId(String chargeBoxId);
    List<TransactionEntity> findByChargeBoxIdAndConnectorId(String chargeBoxId, Integer connectorId);
    boolean existsByTransactionId(Integer transactionId);

//    @Modifying
//    @Query("UPDATE TransactionEntity t SET t = :entity WHERE t.transactionId = :transactionId")
//    void updateByTransactionId(@Param("transactionId") Integer transactionId,
//                               @Param("entity") TransactionEntity entity);
}
