package charg.ing.stations.dto.dengi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * "data" payload returned by statusPayment.
 * When the QR has not been scanned the API returns only {@code status} (e.g. "processing").
 * Once there is at least one attempt, {@code payments} is populated.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusPaymentResult {

    /** Top-level status when there are no payment attempts yet (processing/approved/canceled). */
    private String status;

    private List<DengiPayment> payments;

    private Integer error;
    private String desc;

    public boolean isError() {
        return error != null;
    }
}
