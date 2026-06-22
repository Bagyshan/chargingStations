package charg.ing.stations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API gateway: единая точка входа, агрегирующая Swagger/OpenAPI документацию всех сервисов.
 * Построен на Spring Cloud Gateway (реактивный стек, WebFlux/Netty).
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
