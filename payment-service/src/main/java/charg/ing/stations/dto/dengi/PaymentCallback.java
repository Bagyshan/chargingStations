package charg.ing.stations.dto.dengi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Body O!Dengi POSTs to our result_url when the transaction status changes.
 * status_pay: 2 = canceled (refunded), 3 = approved (successful final payment).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentCallback {

    @JsonProperty("trans_id")
    private String transId;

    @JsonProperty("status_pay")
    private Integer statusPay;

    @JsonProperty("site_id")
    private String siteId;

    @JsonProperty("order_id")
    private String orderId;

    /** Amount in kopecks. */
    private Long amount;

    private String currency;
    private String mktime;
    private Integer test;

    @JsonProperty("account_id")
    private String accountId;

    private String mobile;
    private String fname;
    private String lname;
    private String email;

    private String hash;

    public static final int STATUS_CANCELED = 2;
    public static final int STATUS_APPROVED = 3;
}
