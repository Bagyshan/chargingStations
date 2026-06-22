package charg.ing.stations.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeignCustomErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() >= 400 && response.status() <= 499) {
            log.error("Client error when calling {} with status: {}", methodKey, response.status());
            // Можно выбросить кастомное исключение
        } else if (response.status() >= 500) {
            log.error("Server error when calling {} with status: {}", methodKey, response.status());
        }

        return defaultErrorDecoder.decode(methodKey, response);
    }
}