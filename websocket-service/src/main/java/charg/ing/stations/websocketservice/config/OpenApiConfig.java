package charg.ing.stations.websocketservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WebSocket Service API")
                        .description("WebSocket service for real-time station events")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Charging Stations Team")
                                .email("support@example.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8003")
                                .description("Local development server")
                ));
    }
}