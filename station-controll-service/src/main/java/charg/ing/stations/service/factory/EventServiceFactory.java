package charg.ing.stations.service.factory;

import charg.ing.stations.service.ChargeBoxService;
import charg.ing.stations.service.ConnectorService;
import charg.ing.stations.service.TransactionService;
import charg.ing.stations.service.interfaces.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EventServiceFactory {

    private final ChargeBoxService chargeBoxService;
    private final ConnectorService connectorService;
    private final TransactionService transactionService;

    @Autowired
    public EventServiceFactory(ChargeBoxService chargeBoxService, ConnectorService connectorService, TransactionService transactionService) {
        this.chargeBoxService = chargeBoxService;
        this.connectorService = connectorService;
        this.transactionService = transactionService;
    }

    public EventService getService(String actionType) {
        return switch (actionType) {
            case "CHARGE_BOX" -> chargeBoxService;
            case "CONNECTOR" -> connectorService;
            case "START_TRANSACTION" -> transactionService;
            case "STOP_TRANSACTION" -> transactionService;
            default -> throw new IllegalArgumentException("Unknown action type: " + actionType);
        };
    }
}