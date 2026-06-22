package charg.ing.stations.dto.dengi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * One payment attempt inside statusPayment "payments" array.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DengiPayment {

    @JsonProperty("trans_id")
    private String transId;

    @JsonProperty("date_pay")
    private String datePay;

    /** Amount in kopecks (string). */
    private String amount;

    @JsonProperty("amount_old")
    private String amountOld;

    /** approved | processing | canceled */
    private String status;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("payment_id")
    private String paymentId;

    private String test;
    private String description;
    private String mobile;
    private String email;
    private String fname;
    private String lname;
}
