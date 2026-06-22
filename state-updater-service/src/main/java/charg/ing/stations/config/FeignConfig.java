package charg.ing.stations.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
//import feign.jackson.JacksonDecoder;
//import feign.jackson.JacksonEncoder;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

//    @Bean
//    public Logger.Level feignLoggerLevel() {
//        return Logger.Level.FULL;
//    }
//
//    @Bean
//    public ErrorDecoder errorDecoder() {
//        return new FeignCustomErrorDecoder();
//    }
//
//    @Bean
//    public Retryer retryer() {
//        return new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 3);
//    }
//
//    @Bean
//    public Request.Options options() {
//        return new Request.Options(5, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, true);
//    }


//    @Bean
//    public HttpMessageConverters httpMessageConverters(ObjectMapper objectMapper) {
//        return new HttpMessageConverters(
//                new MappingJackson2HttpMessageConverter(objectMapper)
//        );
//    }
//
//    @Bean
//    public Decoder feignDecoder(ObjectMapper objectMapper) {
//        HttpMessageConverter<?> jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
//        ObjectFactory<HttpMessageConverters> objectFactory = () -> new HttpMessageConverters(jacksonConverter);
//        return new ResponseEntityDecoder(new SpringDecoder(objectFactory));
//    }

//    @Bean
//    public Decoder feignDecoder() {
//        ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(new JavaTimeModule());
//        return new JacksonDecoder(mapper);
//    }
//
//    @Bean
//    public Encoder feignEncoder() {
//        ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(new JavaTimeModule());
//        return new JacksonEncoder(mapper);
//    }
}