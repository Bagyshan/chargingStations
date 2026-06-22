package charg.ing.stations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the O!Dengi (dengi.kg) QR-pay merchant API.
 * Values are taken from the merchant admin panel (Торговые точки -> Все торговые точки).
 */
@ConfigurationProperties(prefix = "dengi")
public class DengiProperties {

    /** Endpoint of the JSON API, e.g. https://mw-api-test.dengi.kg/api/json/json.php */
    private String apiUrl;

    /** Merchant SID (Идентификатор торговца). */
    private String sid;

    /** Merchant API password (Пароль для API торговца) — used to sign requests. */
    private String password;

    /** API version, usually 1005. */
    private int version = 1005;

    /** Response/notification language: "ru" or "en". */
    private String lang = "ru";

    /** 1 = test (sandbox) payment, 0 = production payment. */
    private int test = 1;

    /** Public URL that O!Dengi calls with status updates (result_url). */
    private String resultUrl;

    /** Reject incoming result_url callbacks whose HMAC-MD5 hash does not match. */
    private boolean verifyCallbackHash = true;

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public int getTest() { return test; }
    public void setTest(int test) { this.test = test; }

    public String getResultUrl() { return resultUrl; }
    public void setResultUrl(String resultUrl) { this.resultUrl = resultUrl; }

    public boolean isVerifyCallbackHash() { return verifyCallbackHash; }
    public void setVerifyCallbackHash(boolean verifyCallbackHash) { this.verifyCallbackHash = verifyCallbackHash; }
}
