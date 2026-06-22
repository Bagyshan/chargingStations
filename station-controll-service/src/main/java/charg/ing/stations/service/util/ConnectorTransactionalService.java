package charg.ing.stations.service.util;

import charg.ing.stations.dto.ConnectorCreateEvent;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.repository.ConnectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Slf4j
@Service
public class ConnectorTransactionalService {

    private final ConnectorRepository repository;

    public ConnectorTransactionalService(ConnectorRepository repository) {
        this.repository = repository;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW)
    public void saveIfNotExists(ConnectorCreateEvent req, Runnable afterCommit) {

        if (repository.existsByConnectorId(req.getConnectorId())) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            afterCommit.run();
                        }
                    }
            );
            return;
        }

        ConnectorEntity entity = new ConnectorEntity();
        entity.setChargeBoxId(req.getChargeBoxId());
        entity.setConnectorId(req.getConnectorId());
        entity.setInfo(req.getInfo());
        entity.setActionType(req.getActionType());
        entity.setVendorId(req.getVendorId());
        entity.setCreatedAt(
                req.getTimestamp() != null ? req.getTimestamp() : Instant.now()
        );
        repository.save(entity);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        afterCommit.run();
                    }
                }
        );
    }
}
