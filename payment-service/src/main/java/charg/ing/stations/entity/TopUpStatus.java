package charg.ing.stations.entity;

/** Lifecycle of a wallet top-up invoice. */
public enum TopUpStatus {
    /** Invoice created, waiting for the payer to pay the QR. */
    PENDING,
    /** Payment confirmed by O!Dengi — wallet credited. */
    APPROVED,
    /** Invoice canceled / expired / refunded. */
    CANCELED,
    /** Invoice could not be created at the gateway. */
    FAILED
}
