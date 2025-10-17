package de.rwth.idsg.steve.web.api.repository;

import de.rwth.idsg.steve.repository.dto.Transaction;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.CrudRepository;
//import org.springframework.data.repository.query.Param;
//
//import java.util.Optional;
//
//public interface ApiTransactionRepository extends CrudRepository<Transaction, Integer> {
//
//    @Query(value = """
//        SELECT t.*
//        FROM transaction t
//        WHERE t.charge_box_id = :chargeBoxId
//          AND t.connector_id = :connectorId
//          AND t.stop_timestamp IS NULL
//        ORDER BY t.start_timestamp DESC
//        LIMIT 1
//        """, nativeQuery = true)
//    Transaction getActiveTransaction(
//            @Param("chargeBoxId") String chargeBoxId,
//            @Param("connectorId") Integer connectorId
//    );
//}
