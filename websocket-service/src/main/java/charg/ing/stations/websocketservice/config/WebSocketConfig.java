package charg.ing.stations.websocketservice.config;

import charg.ing.stations.websocketservice.handler.StationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
//@EnableWebSocket
public class WebSocketConfig {

    private final StationWebSocketHandler stationWebSocketHandler;

    @Bean
    public HandlerMapping handlerMapping() {
        Map<String, StationWebSocketHandler> map = new HashMap<>();
        map.put("/ws/station-events", stationWebSocketHandler);
        map.put("/ws/station-events/", stationWebSocketHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(-1); // Высокий приоритет

        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}

// ws://localhost:8003/ws/station-events