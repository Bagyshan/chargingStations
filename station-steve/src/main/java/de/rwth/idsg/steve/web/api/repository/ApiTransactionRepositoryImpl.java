package de.rwth.idsg.steve.web.api.repository;

//import de.rwth.idsg.steve.repository.dto.Transaction;
//import de.rwth.idsg.steve.web.api.repository.dto.ApiTransactionStartResponse;
//import jooq.steve.db.tables.records.TransactionRecord;
//import org.jooq.DSLContext;
//import org.jooq.Name;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Repository;
//
//import java.util.Arrays;
//import java.util.Map;
//
//import static jooq.steve.db.tables.Connector.CONNECTOR;
//import static jooq.steve.db.tables.Transaction.TRANSACTION;
//
//@Repository
//public class ApiTransactionRepositoryImpl implements ApiTransactionRepository {
//
//    private final DSLContext ctx;
//
//    @Autowired
//    public ApiTransactionRepositoryImpl(DSLContext ctx) {
//        this.ctx = ctx;
//    }
//
//    @Override
//    public ApiTransactionStartResponse getActiveTransaction(String chargeBoxId, Integer connectorId) {
//        return ctx.select(TRANSACTION.TRANSACTION_PK,
//                        TRANSACTION.START_TIMESTAMP)
//                .from(TRANSACTION)
//                .join(CONNECTOR)
//                .on(TRANSACTION.CONNECTOR_PK.equal(CONNECTOR.CONNECTOR_PK))
//                .and(CONNECTOR.CHARGE_BOX_ID.equal(chargeBoxId))
//                .where(TRANSACTION.STOP_TIMESTAMP.isNull())
//                .and(CONNECTOR.CONNECTOR_ID.equal(connectorId))
//                .orderBy(TRANSACTION.TRANSACTION_PK.desc()) // to avoid fetching ghost transactions, fetch the latest
//                .fetchAny(ApiTransactionStartResponse.class);
//    }
//
//}
