package charg.ing.stations.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ConnectorStatus {
    AVAILABLE("Available"),
    PREPARING("Preparing"),
    CHARGING("Charging"),
    SUSPENDED_EV("SuspendedEV"),
    SUSPENDED_EVSE("SuspendedEVSE"),
    RESERVED("Reserved"),
    UNAVAILABLE("Unavailable"),
    FAULTED("Faulted");

    private final String value;

    public static ConnectorStatus fromValue(String value) {
        for (ConnectorStatus status : ConnectorStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown connector status: " + value);
    }
}