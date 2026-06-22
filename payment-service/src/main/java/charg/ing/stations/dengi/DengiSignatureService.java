package charg.ing.stations.dengi;

import charg.ing.stations.config.DengiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Signs and verifies O!Dengi (dengi.kg) messages using HMAC-MD5 with the merchant API password as key.
 *
 * <p>Two distinct rules per the «Формирование hash» spec:
 * <ul>
 *   <li><b>Outgoing requests:</b> {@code HMAC-MD5(key=password, message=full request JSON without the hash field)}
 *       — see {@link DengiClient} which signs the exact serialized body.</li>
 *   <li><b>result_url callbacks:</b> {@code HMAC-MD5(key=password,
 *       message="trans_id:::status_pay:::site_id:::order_id:::amount:::currency:::mktime:::test")}.</li>
 * </ul>
 */
@Component
@Slf4j
public class DengiSignatureService {

    private final DengiProperties props;

    public DengiSignatureService(DengiProperties props) {
        this.props = props;
    }

    /** Signs the exact, minified request JSON string (without the {@code hash} field). */
    public String signRequestJson(String requestJsonWithoutHash) {
        return hmacMd5Hex(requestJsonWithoutHash, props.getPassword());
    }

    /** Verifies the {@code hash} of an incoming result_url callback (constant-time). */
    public boolean verifyCallback(JsonNode body) {
        String received = text(body, "hash").toLowerCase(Locale.ROOT);
        if (received.length() != 32) {
            return false;
        }
        String computed = hmacMd5Hex(buildCallbackMessage(body), props.getPassword()).toLowerCase(Locale.ROOT);
        boolean ok = constantTimeEquals(received, computed);
        if (!ok) {
            log.warn("O!Dengi callback hash mismatch: received={}, computed={}", received, computed);
        }
        return ok;
    }

    static String buildCallbackMessage(JsonNode n) {
        return String.join(":::",
                text(n, "trans_id"),
                text(n, "status_pay"),
                text(n, "site_id"),
                text(n, "order_id"),
                text(n, "amount"),
                text(n, "currency"),
                text(n, "mktime"),
                text(n, "test"));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? "" : v.asText();
    }

    static String hmacMd5Hex(String message, String password) {
        try {
            Mac mac = Mac.getInstance("HmacMD5");
            mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "HmacMD5"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("O!Dengi HMAC-MD5 failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int d = 0;
        for (int i = 0; i < x.length; i++) {
            d |= x[i] ^ y[i];
        }
        return d == 0;
    }
}
