package charg.ing.stations.dengi;

import charg.ing.stations.config.DengiProperties;
import charg.ing.stations.dto.dengi.CreateInvoiceResult;
import charg.ing.stations.dto.dengi.StatusPaymentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
// DengiJson.MAPPER is used for exact request serialization (see DengiJson).
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reactive client for the O!Dengi (dengi.kg) QR-pay JSON API.
 * Envelope: { cmd, version, sid, mktime, lang, data, hash }; hash is HMAC-MD5 over the body without hash.
 */
@Component
@Slf4j
public class DengiClient {

    private final WebClient webClient;
    private final DengiProperties props;
    private final DengiSignatureService signatureService;
    private final ObjectMapper mapper;

    public DengiClient(@Qualifier("dengiWebClient") WebClient webClient,
                       DengiProperties props,
                       DengiSignatureService signatureService) {
        this.webClient = webClient;
        this.props = props;
        this.signatureService = signatureService;
        this.mapper = DengiJson.MAPPER;
    }

    /**
     * createInvoice — issues an invoice and returns the QR / deeplink / paylink data.
     *
     * @param orderId       unique merchant order id (<=64 chars)
     * @param amountKopecks amount in kopecks/tyiyns (100 = 1.00 KGS), or null to let the payer enter it
     * @param description   payment description shown to the payer
     */
    public Mono<CreateInvoiceResult> createInvoice(String orderId, Long amountKopecks, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order_id", orderId);
        data.put("desc", description);
        data.put("amount", amountKopecks);
        data.put("currency", "KGS");
        data.put("test", props.getTest());
        data.put("long_term", 0); // one-time QR
        data.put("user_to", null);
        data.put("date_life", null);
        data.put("date_start_push", null);
        data.put("count_push", null);
        data.put("send_push", 1);
        data.put("send_sms", 1);
        data.put("success_url", null);
        data.put("fail_url", null);
        data.put("fields_other", null);
        data.put("transtype", null);
        data.put("result_url", props.getResultUrl());
        return post("createInvoice", data, CreateInvoiceResult.class);
    }

    /** statusPayment — returns the current status of an invoice by invoice_id (and order_id). */
    public Mono<StatusPaymentResult> statusPayment(String invoiceId, String orderId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoice_id", invoiceId);
        data.put("order_id", orderId);
        data.put("mark", null);
        return post("statusPayment", data, StatusPaymentResult.class);
    }

    private <T> Mono<T> post(String cmd, Map<String, Object> data, Class<T> dataType) {
        final String body = buildSignedBody(cmd, data);
        return webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(raw -> parseResponse(cmd, raw, dataType))
                .doOnError(e -> log.error("O!Dengi {} request failed", cmd, e));
    }

    /** Builds {cmd,version,sid,mktime,lang,data}, signs the exact serialized bytes, appends hash. */
    private String buildSignedBody(String cmd, Map<String, Object> data) {
        String mktime = String.valueOf(Instant.now().toEpochMilli());
        ObjectNode root = mapper.createObjectNode();
        root.put("cmd", cmd);
        root.put("version", props.getVersion());
        root.put("sid", props.getSid());
        root.put("mktime", mktime);
        root.put("lang", props.getLang());
        root.set("data", mapper.valueToTree(data));
        try {
            String jsonWithoutHash = mapper.writeValueAsString(root);
            root.put("hash", signatureService.signRequestJson(jsonWithoutHash));
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build O!Dengi " + cmd + " request", e);
        }
    }

    private <T> T parseResponse(String cmd, String raw, Class<T> dataType) {
        try {
            JsonNode root = mapper.readTree(raw);
            log.debug("O!Dengi {} response: {}", cmd, root);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || dataNode.isNull()) {
                throw new DengiApiException(cmd, null, "Empty 'data' in O!Dengi response");
            }
            JsonNode errorNode = dataNode.get("error");
            if (errorNode != null && !errorNode.isNull()) {
                String desc = dataNode.path("desc").asText(null);
                throw new DengiApiException(cmd, errorNode.asInt(), desc);
            }
            return mapper.convertValue(dataNode, dataType);
        } catch (DengiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DengiApiException(cmd, null, "Failed to parse O!Dengi response: " + e.getMessage());
        }
    }
}
