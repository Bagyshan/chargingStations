package charg.ing.stations.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Priority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

import java.time.Duration;
import java.util.List;

@Configuration
public class RedisConfig {


    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.geo.redis.database:1}")
    private int geoDatabase;

    // Основная фабрика подключения для базы 0 (стандартная)

    @Bean
    @Primary
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(2000))
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redisHost, redisPort);
        factory.setDatabase(redisDatabase);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            factory.setPassword(redisPassword);
        }
        factory.setTimeout(2000);
        factory.afterPropertiesSet();
        return factory;
    }

    // Фабрика подключения для базы 1 (геоданные)
    @Bean(name = "geoRedisConnectionFactory")
    public ReactiveRedisConnectionFactory geoRedisConnectionFactory() {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(2000))
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redisHost, redisPort);
        factory.setDatabase(geoDatabase);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            factory.setPassword(redisPassword);
        }
        factory.setTimeout(2000);
        factory.afterPropertiesSet();
        return factory;
    }


    // Основной RedisTemplate для базы 0
    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory,
            ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<String> serializer =
                new Jackson2JsonRedisSerializer<>(String.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, String> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, String> context =
                builder.value(serializer).build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    // RedisTemplate для геоданных (база 1)
    // RedisTemplate для JSON данных (база 1)
    @Bean(name = "geoRedisTemplate")
    public ReactiveRedisTemplate<String, String> geoReactiveRedisTemplate(
            @Qualifier("geoRedisConnectionFactory") ReactiveRedisConnectionFactory factory,
            ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<String> serializer =
                new Jackson2JsonRedisSerializer<>(String.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, String> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, String> context =
                builder.value(serializer).build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    // ReactiveStringRedisTemplate для GEO команд (более удобный для геоданных)
    @Bean(name = "geoStringRedisTemplate")
    public ReactiveStringRedisTemplate geoStringRedisTemplate(
            @Qualifier("geoRedisConnectionFactory") ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}