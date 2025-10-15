package charg.ing.stations.config;


import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import java.util.HashMap;
import java.util.Map;

@Configuration
//@EnableKafkaStreams
public class KafkaStreamsConfig {
//
//    @Bean
//    public KafkaStreamsConfiguration kafkaStreamsConfig() {
//        Map<String, Object> props = new HashMap<>();
//        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "station-state-processor");
//        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"); // Укажите ваши серверы
//        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
//        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
//        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, "exactly_once_v2");
//        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0); // Отключить кэш для мгновенных обновлений
//        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100); // Частые коммиты
//        return new KafkaStreamsConfiguration(props);
//    }
//
//    @Bean
//    public KTable<String, String> stationStateTable(StreamsBuilder builder) {
//        // Читаем из исходного топика
//        KTable<String, String> table = builder
//                .table("station.state",
//                        Materialized.as("station-state-store"));
//
//        // Отправляем только последние значения в новый топик
//        table.toStream().to("station.state.compacted");
//
//        return table;
//    }
}
