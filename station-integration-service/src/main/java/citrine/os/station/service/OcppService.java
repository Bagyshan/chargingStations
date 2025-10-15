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




@Service
@RequiredArgsConstructor
public class OcppService {

    private final SteveClient steveClient;
    private final OcppResponseProducer ocppResponseProducer;

    public Mono<ResponseEntity<OcppStartResponse>> remoteStart(RemoteStartRequest req) {
        return steveClient.remoteStart(req)
                .flatMap(response -> {
                    // достаём тело ответа
                    RemoteStartResponse body = response.getBody();
                    if (body == null) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
                    }

                    // создаём OcppStartResponse из тела (или из полей, которые тебе нужны)
                    OcppStartResponse ocpp = new OcppStartResponse(
                            body.getTransactionId(),
                            body.getChargeBoxId(),  // условно, если у тебя есть такой метод
                            body.getConnectorId(),
                            body.getOcppTag(),
                            body.getStartValue(),
                            ActionType.START_TRANSACTION,
                            TransactionStatus.ACTIVE,
                            body.getStartTimestamp()
                    );

                    // отправляем в Kafka (пусть метод возвращает Mono<Void>)
                    ocppResponseProducer.sendStartResponse(ocpp);
                    return Mono.just(ResponseEntity.status(response.getStatusCode()).body(ocpp));
        });
    }

    public Mono<ResponseEntity<OcppStopResponse>> remoteStop(RemoteStopRequest req) {
        return steveClient.remoteStop(req)
                .flatMap(response -> {
                    // достаём тело ответа
                    RemoteStopResponse body = response.getBody();
                    if (body == null) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
                    }

                    // создаём OcppStartResponse из тела (или из полей, которые тебе нужны)
                    OcppStopResponse ocpp = new OcppStopResponse(
                            body.getTransactionId(),
                            body.getChargeBoxId(),  // условно, если у тебя есть такой метод
                            body.getConnectorId(),
                            body.getOcppTag(),
                            body.getStartValue(),
                            body.getStopValue(),
                            ActionType.STOP_TRANSACTION,
                            TransactionStatus.COMPLETED,
                            body.getStopTimestamp()
                    );

                    // отправляем в Kafka (пусть метод возвращает Mono<Void>)
                    ocppResponseProducer.sendStopResponse(ocpp);
                    return Mono.just(ResponseEntity.status(response.getStatusCode()).body(ocpp));
                });
    }
}
