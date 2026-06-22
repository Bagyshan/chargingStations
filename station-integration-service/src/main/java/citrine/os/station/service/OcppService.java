package citrine.os.station.service;


import citrine.os.station.client.SteveClient;
import citrine.os.station.dto.*;
import citrine.os.station.enums.ActionType;
import citrine.os.station.enums.TransactionStatus;
import citrine.os.station.producer.OcppResponseProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class OcppService {

    private final SteveClient steveClient;
    private final OcppResponseProducer ocppResponseProducer;

//    public Mono<ResponseEntity<OcppResponse>> remoteStart(RemoteStartRequest req, String userId) {
//        return steveClient.remoteStart(req)
//                .flatMap(response -> {
//                    // достаём тело ответа
//                    RemoteStartResponse body = response.getBody();
//                    if (body == null) {
//                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
//                    }
//
//                    OcppResponse ocpp = OcppResponse.builder()
//                            .transactionId(body.getTransactionId())
//                            .chargeBoxId(body.getChargeBoxId())
//                            .connectorId(body.getConnectorId())
//                            .ocppTag(body.getOcppTag())
//                            .actionType(ActionType.START_TRANSACTION)
//                            .status(TransactionStatus.ACTIVE)
//                            .userId(userId)
//                            .startTimestamp(body.getStartTimestamp())
//                            .build();
//
//                    // отправляем в Kafka (пусть метод возвращает Mono<Void>)
//                    ocppResponseProducer.sendStartResponse(ocpp);
//                    return Mono.just(ResponseEntity.status(response.getStatusCode()).body(ocpp));
//        });
//    }

    public Mono<OcppResponse> remoteStart(RemoteStartRequest req, UUID correlationId) {
        return steveClient.remoteStart(req)
                .map(responseEntity -> {
                    RemoteStartResponse body = responseEntity.getBody();
                    if (body == null || !responseEntity.getStatusCode().is2xxSuccessful()) {
                        return OcppResponse.builder()
                                .correlationId(correlationId)
                                .errorMessage("Failed to start transaction: " + responseEntity.getStatusCode())
                                .build();
                    }
                    return OcppResponse.builder()
                            .correlationId(correlationId)
                            .transactionId(body.getTransactionId())
                            .chargeBoxId(body.getChargeBoxId())
                            .connectorId(body.getConnectorId())
                            .ocppTag(body.getOcppTag())
                            .startValue(body.getStartValue())
                            .actionType(ActionType.START_TRANSACTION)
                            .status(TransactionStatus.ACTIVE)
                            .startTimestamp(body.getStartTimestamp())
                            .build();
                })
                .onErrorResume(e -> Mono.just(OcppResponse.builder()
                        .correlationId(correlationId)
                        .errorMessage("Exception: " + e.getMessage())
                        .build()));
    }

//    public Mono<ResponseEntity<OcppResponse>> remoteStop(RemoteStopRequest req, String userId) {
//        return steveClient.remoteStop(req)
//                .flatMap(response -> {
//                    // достаём тело ответа
//                    RemoteStopResponse body = response.getBody();
//                    if (body == null) {
//                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
//                    }
//
//                    OcppResponse ocpp = OcppResponse.builder()
//                            .transactionId(body.getTransactionId())
//                            .chargeBoxId(body.getChargeBoxId())
//                            .connectorId(body.getConnectorId())
//                            .ocppTag(body.getOcppTag())
//                            .startValue(body.getStartValue())
//                            .stopValue(body.getStopValue())
//                            .actionType(ActionType.STOP_TRANSACTION)
//                            .status(TransactionStatus.COMPLETED)
//                            .userId(userId)
//                            .stopTimestamp(body.getStopTimestamp())
//                            .build();
//
//                    // отправляем в Kafka (пусть метод возвращает Mono<Void>)
//                    ocppResponseProducer.sendStopResponse(ocpp);
//                    return Mono.just(ResponseEntity.status(response.getStatusCode()).body(ocpp));
//                });
//    }

    public Mono<OcppResponse> remoteStop(RemoteStopRequest req, UUID correlationId) {
        return steveClient.remoteStop(req)
                .map(responseEntity -> {
                    RemoteStopResponse body = responseEntity.getBody();
                    if (body == null || !responseEntity.getStatusCode().is2xxSuccessful()) {
                        return OcppResponse.builder()
                                .correlationId(correlationId)
                                .errorMessage("Failed to stop transaction: " + responseEntity.getStatusCode())
                                .build();
                    }
                    return OcppResponse.builder()
                            .correlationId(correlationId)
                            .transactionId(body.getTransactionId())
                            .chargeBoxId(body.getChargeBoxId())
                            .connectorId(body.getConnectorId())
                            .ocppTag(body.getOcppTag())
                            .startValue(body.getStartValue())
                            .stopValue(body.getStopValue())
                            .actionType(ActionType.STOP_TRANSACTION)
                            .status(TransactionStatus.COMPLETED)
                            .stopTimestamp(body.getStopTimestamp())
                            .build();
                })
                .onErrorResume(e -> Mono.just(OcppResponse.builder()
                        .correlationId(correlationId)
                        .errorMessage("Exception: " + e.getMessage())
                        .build()));
    }
}
