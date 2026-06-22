package charg.ing.stations.dto.dengi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * "data" payload returned by createInvoice.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateInvoiceResult {

    @JsonProperty("invoice_id")
    private String invoiceId;

    /** URL of the QR image. */
    private String qr;

    @JsonProperty("emv_qr")
    private String emvQr;

    /** Deeplink to open the invoice inside the (TEST) app. */
    @JsonProperty("link_app")
    private String linkApp;

    /** Paylink web page with buttons for popular banks. */
    @JsonProperty("paylink_url")
    private String paylinkUrl;

    @JsonProperty("site_pay")
    private String sitePay;

    /** Set when the API returns an error object instead of a result. */
    private Integer error;
    private String desc;

    public boolean isError() {
        return error != null;
    }
}
