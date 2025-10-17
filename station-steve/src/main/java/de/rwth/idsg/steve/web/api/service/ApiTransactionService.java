package de.rwth.idsg.steve.web.api.service;

//import com.google.protobuf.Api;
//import de.rwth.idsg.steve.repository.TransactionRepository;
//import de.rwth.idsg.steve.repository.dto.Transaction;
//import de.rwth.idsg.steve.web.api.repository.ApiTransactionRepository;
//import lombok.AllArgsConstructor;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.Optional;
//
//
//@Service
//public class ApiTransactionService {
//
//    private final ApiTransactionRepository apiTransactionRepository;
//
//    @Autowired
//    public ApiTransactionService(ApiTransactionRepository apiTransactionRepository) {
//        this.apiTransactionRepository = apiTransactionRepository;
//    }
//
//    public Transaction getTransaction(String chargeBoxId, Integer connectorId) {
//        return apiTransactionRepository.getActiveTransaction(chargeBoxId, connectorId);
//    }
//}
