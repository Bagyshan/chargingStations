package charg.ing.stations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.RequestPredicates;

import java.net.URI;

@Configuration
public class SwaggerUiConfig {

    @Bean
    public RouterFunction<ServerResponse> swaggerUiRouter() {
        return RouterFunctions.route()
                // Перенаправляем /swagger-ui на /swagger-ui/index.html
                .route(RequestPredicates.GET("/swagger-ui"),
                        request -> ServerResponse.permanentRedirect(URI.create("/swagger-ui/index.html")).build())
                .build();
    }
}