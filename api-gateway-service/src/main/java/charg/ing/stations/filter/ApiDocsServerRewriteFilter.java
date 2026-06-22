package charg.ing.stations.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Переписывает поле {@code servers} в проксируемых OpenAPI-документах сервисов на относительный
 * префикс шлюза (например {@code /booking}).
 *
 * <p>По умолчанию каждый сервис отдаёт абсолютный {@code servers.url} на самого себя
 * (например {@code http://localhost:8008}). Тогда «Try it out» в агрегированном Swagger UI шлёт
 * запрос напрямую в сервис с origin шлюза — это кросс-доменный запрос и падает с CORS / Failed to
 * fetch. Подменяя server на относительный префикс, мы заставляем браузер обращаться к тому же
 * origin (шлюзу): {@code /booking/...} → route {@code /booking/**} → StripPrefix=1 → сервис.
 */
@Component
public class ApiDocsServerRewriteFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ApiDocsServerRewriteFilter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isApiDocsPath(path)) {
            return chain.filter(exchange);
        }
        log.debug("[api-docs-rewrite] intercept path={}", path);

        // Префикс = первый сегмент пути запроса, напр. "/booking" из "/booking/api-docs".
        String[] segments = path.split("/");
        if (segments.length < 2 || segments[1].isBlank()) {
            return chain.filter(exchange);
        }
        String prefix = "/" + segments[1];

        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatusCode status = getStatusCode();
                log.debug("[api-docs-rewrite] writeWith status={} prefix={}", status, prefix);
                if (status == null || status.is2xxSuccessful()) {
                    return super.writeWith(
                            DataBufferUtils.join(Flux.from(body)).map(buffer -> {
                                byte[] content = new byte[buffer.readableByteCount()];
                                buffer.read(content);
                                DataBufferUtils.release(buffer);

                                byte[] modified = rewriteServers(content, prefix);
                                originalResponse.getHeaders().setContentLength(modified.length);
                                return bufferFactory.wrap(modified);
                            }));
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private boolean isApiDocsPath(String path) {
        return path.endsWith("/v3/api-docs") || path.endsWith("/api-docs");
    }

    private byte[] rewriteServers(byte[] content, String prefix) {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root instanceof ObjectNode obj) {
                ArrayNode servers = objectMapper.createArrayNode();
                ObjectNode server = objectMapper.createObjectNode();
                server.put("url", prefix);
                server.put("description", "via api-gateway");
                servers.add(server);
                obj.set("servers", servers);
                return objectMapper.writeValueAsBytes(obj);
            }
        } catch (Exception ignored) {
            // не JSON / не openapi — отдаём как есть
        }
        return content;
    }

    @Override
    public int getOrder() {
        // Раньше NettyWriteResponseFilter (-1), чтобы успеть подменить ответ.
        return -2;
    }
}
