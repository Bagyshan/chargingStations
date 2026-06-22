package charg.ing.stations.controller;

import charg.ing.stations.config.DengiProperties;
import charg.ing.stations.dengi.DengiJson;
import charg.ing.stations.dengi.DengiSignatureService;
import charg.ing.stations.dto.dengi.PaymentCallback;
import charg.ing.stations.service.TopUpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Public endpoint registered as {@code result_url} for O!Dengi invoices.
 * O!Dengi POSTs here on status change / final result; we verify the HMAC-MD5 hash, ack with 200,
 * and credit the wallet on status_pay=3 (approved).
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "payment-callback", description = "Webhook O!Dengi (result_url)")
@Slf4j
public class PaymentCallbackController {

    private final TopUpService topUpService;
    private final DengiSignatureService signatureService;
    private final DengiProperties props;
    private final ObjectMapper mapper;

    public PaymentCallbackController(TopUpService topUpService,
                                     DengiSignatureService signatureService,
                                     DengiProperties props) {
        this.topUpService = topUpService;
        this.signatureService = signatureService;
        this.props = props;
        this.mapper = DengiJson.MAPPER;
    }

    @Operation(summary = "Приём уведомлений O!Dengi об изменении статуса платежа")
    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> callback(@RequestBody String body) {
        final JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception e) {
            log.warn("O!Dengi callback: cannot parse body: {}", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().build());
        }
        log.info("O!Dengi callback received: {}", root);

        if (props.isVerifyCallbackHash() && !signatureService.verifyCallback(root)) {
            log.warn("Rejecting O!Dengi callback: bad hash");
            return Mono.just(ResponseEntity.badRequest().build());
        }

        String siteId = root.path("site_id").asText("");
        if (!siteId.isEmpty() && !siteId.equals(props.getSid())) {
            log.warn("O!Dengi callback: site_id mismatch got={} expected={}", siteId, props.getSid());
            return Mono.just(ResponseEntity.badRequest().build());
        }

        PaymentCallback cb = mapper.convertValue(root, PaymentCallback.class);
        return topUpService.handleCallback(cb)
                .thenReturn(ResponseEntity.ok().<Void>build())
                .onErrorResume(err -> {
                    log.error("Error handling O!Dengi callback", err);
                    // 200 anyway: O!Dengi does not consume our response, and the poller will reconcile.
                    return Mono.just(ResponseEntity.ok().build());
                });
    }
}
