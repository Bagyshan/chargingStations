package charg.ing.stations.producer;


import charg.ing.stations.dto.StationHourlyTariffsEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Service
@RequiredArgsConstructor
public class StationHourlyTariffProducer {

    private final KafkaSender<String, Object> kafkaSender;

    private static final String TOPIC =
            "station.hourly.tariffs";

    public Mono<Void> send(
            StationHourlyTariffsEvent event
    ) {

        SenderRecord<String, Object, String> record =
                SenderRecord.create(
                        TOPIC,
                        null,
                        null,
                        "hour-" + System.currentTimeMillis(),
                        event,
                        null
                );

        return kafkaSender
                .send(Mono.just(record))
                .next()
                .then();
    }
}