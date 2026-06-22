package citrine.os.station.consumer;

import citrine.os.station.dto.OcppRequest;
import citrine.os.station.dto.OcppResponse;
import citrine.os.station.dto.RemoteStartRequest;
import citrine.os.station.dto.RemoteStopRequest;
import citrine.os.station.service.OcppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcppRequestConsumer {

    private final OcppService ocppService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ocpp.requests", groupId = "station-integration-service")
    public void consume(Map<String, Object> payload, Acknowledgment ack) {
        log.info("Received OCPP request: {}", payload);

        try {
            OcppRequest request = objectMapper.convertValue(payload, OcppRequest.class);
            UUID correlationId = request.getCorrelationId();
            if (correlationId == null) {
                log.error("Missing correlationId in request");
                ack.acknowledge();
                return;
            }

            String chargeBoxId = request.getChargeBoxId();
            Integer connectorId = request.getConnectorId();
            String ocppTag = request.getOcppTag();

            Mono<OcppResponse> responseMono;
            if (request.getIsStop() == false) {
                // START
                RemoteStartRequest startReq = new RemoteStartRequest(chargeBoxId, connectorId, ocppTag);
                responseMono = ocppService.remoteStart(startReq, correlationId);
            } else {
//                // STOP
                RemoteStopRequest stopReq = new RemoteStopRequest(chargeBoxId, connectorId, ocppTag);
                responseMono = ocppService.remoteStop(stopReq, correlationId);
            }

            responseMono.subscribe(
                    response -> sendResponse(response, chargeBoxId, connectorId, ack, correlationId),
                    error -> {
                        log.error("Error processing request for correlationId: {}", correlationId, error);
                        OcppResponse errorResponse = OcppResponse.builder()
                                .correlationId(correlationId)
                                .errorMessage(error.getMessage())
                                .build();
                        sendResponse(errorResponse, chargeBoxId, connectorId, ack, correlationId);
                    }
            );

        } catch (Exception e) {
            log.error("Failed to parse request", e);
            ack.acknowledge(); // не можем обработать, подтверждаем
        }
    }

    private void sendResponse(OcppResponse response, String key, Integer connectorId, Acknowledgment ack, UUID correlationId) {
        kafkaTemplate.send("ocpp.responses", key + "/" + connectorId, response)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send response to Kafka for correlationId: {}", correlationId, ex);
                        // Не подтверждаем, чтобы повторить попытку отправки позже
                    } else {
                        log.info("Response sent for correlationId: {}", correlationId);
                        ack.acknowledge();
                    }
                });
    }
}