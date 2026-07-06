package charg.ing.stations.websocketservice.handler;

import charg.ing.stations.websocketservice.dto.WebSocketMessageDTO;
import charg.ing.stations.websocketservice.security.JwtTokenValidator;
import charg.ing.stations.websocketservice.service.RedisStreamConsumerService;
import charg.ing.stations.websocketservice.handler.WebSocketSessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.channel.AbortedException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class StationWebSocketHandler implements WebSocketHandler {

    private final RedisStreamConsumerService redisStreamConsumerService;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final JwtTokenValidator tokenValidator; // Добавлено

    private static final Duration PING_INTERVAL = Duration.ofSeconds(30);
    /** Клиент неактивен (нет PONG/сообщений) дольше этого — закрываем сессию. */
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration IDLE_CHECK_INTERVAL = Duration.ofSeconds(30);




    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Извлекаем токен из query параметра
        String query = session.getHandshakeInfo().getUri().getQuery();
        String token = null;
        if (query != null && query.startsWith("token=")) {
            token = query.substring(6);
        }

        if (token == null) {
            log.warn("No token provided, closing connection");
            return session.close();
        }

        String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        AtomicBoolean isActive = new AtomicBoolean(true);

        return tokenValidator.validateTokenAndGetUserId(token)
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid token")))
                .flatMap(userId -> {
                    log.info("🎯 NEW WEBSOCKET CONNECTION:");
                    log.info("  Client ID: {}", clientId);
                    log.info("  Session ID: {}", session.getId());
                    log.info("  Remote Address: {}", session.getHandshakeInfo().getRemoteAddress());
                    log.info("  Headers: {}", session.getHandshakeInfo().getHeaders());
                    log.info("  User ID: {}", userId);

                    // Регистрируем сессию и связываем с userId
                    sessionManager.registerSession(clientId, session);
                    sessionManager.registerUserSession(userId, clientId);

                    // 1. Обработка входящих сообщений
                    Mono<Void> inboundHandler = handleInboundMessages(clientId, session, isActive);

                    // 2. Отправка исходящих сообщений
                    Mono<Void> outboundHandler = handleOutboundMessages(clientId, session, isActive);

                    // 3. Idle-таймаут: живой клиент отвечает PONG на каждый PING
                    // (~30 с), поэтому активная сессия живёт бесконечно; рвём
                    // только тех, кто реально замолчал дольше IDLE_TIMEOUT.
                    Mono<Void> timeoutHandler = Flux.interval(IDLE_CHECK_INTERVAL)
                            .takeWhile(tick -> isActive.get())
                            .filter(tick -> {
                                Instant last = sessionManager.getLastActivity(clientId);
                                return last == null ||
                                        Duration.between(last, Instant.now()).compareTo(IDLE_TIMEOUT) > 0;
                            })
                            .next()
                            .doOnNext(tick -> {
                                log.warn("⏰ Idle timeout for client: {} (no activity for {}s)",
                                        clientId, IDLE_TIMEOUT.toSeconds());
                                isActive.set(false);
                            })
                            .then();

                    return Mono.when(inboundHandler, outboundHandler, timeoutHandler)
                            .doOnSubscribe(s ->
                                    log.info("🚀 Starting WebSocket handler for client: {}", clientId)
                            )
                            .doFinally(signal -> {
                                log.info("🛑 WebSocket handler completed for client {}: {}",
                                        clientId, signal);
                                cleanupConnection(clientId, isActive);
                            })
                            .onErrorResume(error -> {
                                log.error("💥 WebSocket handler error for client {}: {}",
                                        clientId, error.getMessage(), error);
                                cleanupConnection(clientId, isActive);
                                return Mono.empty();
                            });
                })
                .onErrorResume(e -> {
                    log.error("Authentication failed: {}", e.getMessage());
                    return session.close();
                });
    }


//
//    @Override
//    public Mono<Void> handle(WebSocketSession session) {
//        String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
//        AtomicBoolean isActive = new AtomicBoolean(true);
//
//        log.info("🎯 NEW WEBSOCKET CONNECTION:");
//        log.info("  Client ID: {}", clientId);
//        log.info("  Session ID: {}", session.getId());
//        log.info("  Remote Address: {}", session.getHandshakeInfo().getRemoteAddress());
//        log.info("  Headers: {}", session.getHandshakeInfo().getHeaders());
//
//        // Регистрируем сессию
//        sessionManager.registerSession(clientId, session);
//
//        // 1. Обработка входящих сообщений
//        Mono<Void> inboundHandler = handleInboundMessages(clientId, session, isActive);
//
//        // 2. Отправка исходящих сообщений
//        Mono<Void> outboundHandler = handleOutboundMessages(clientId, session, isActive);
//
//        // 3. Обработка таймаута
//        Mono<Void> timeoutHandler = Mono.delay(CONNECTION_TIMEOUT)
//                .doOnNext(tick -> {
//                    if (isActive.get()) {
//                        log.warn("⏰ Connection timeout for client: {}", clientId);
//                        isActive.set(false);
//                    }
//                })
//                .then();
//
//        return Mono.when(inboundHandler, outboundHandler, timeoutHandler)
//                .doOnSubscribe(s ->
//                        log.info("🚀 Starting WebSocket handler for client: {}", clientId)
//                )
//                .doFinally(signal -> {
//                    log.info("🛑 WebSocket handler completed for client {}: {}",
//                            clientId, signal);
//                    cleanupConnection(clientId, isActive);
//                })
//                .onErrorResume(error -> {
//                    log.error("💥 WebSocket handler error for client {}: {}",
//                            clientId, error.getMessage(), error);
//                    cleanupConnection(clientId, isActive);
//                    return Mono.empty();
//                });
//    }

    private Flux<WebSocketMessageDTO> createMessageStream(String clientId, AtomicBoolean isActive) {
        // Ping сообщения
        Flux<WebSocketMessageDTO> pingFlux = Flux.interval(PING_INTERVAL)
                .takeWhile(tick -> isActive.get())
                .map(tick -> {
                    log.trace("🔄 Sending PING to client: {}", clientId);
                    return WebSocketMessageDTO.builder()
                            .type(WebSocketMessageDTO.MessageType.PING)
                            .timestamp(Instant.now().toEpochMilli())
                            .serverTime(System.currentTimeMillis())
                            .build();
                });

        // События из Redis Stream (теперь широковещательные)
        Flux<WebSocketMessageDTO> eventFlux = redisStreamConsumerService.consumeEventsBroadcast(clientId)
                .takeWhile(ignore -> isActive.get() && isActive.get()) // двойная проверка
                .doOnSubscribe(s ->
                        log.info("📡 Starting broadcast Redis event consumption for client: {}", clientId)
                )
                .doOnNext(event ->
                        log.debug("📨 Sending broadcast event to client {}: {}",
                                clientId, event.getEvent().getEventType())
                )
                .doOnError(error -> {
                    if (!isConnectionClosedError(error)) {
                        log.error("❌ Error in broadcast Redis event stream for client {}: {}",
                                clientId, error.getMessage(), error);
                    }
                })
                .doOnCancel(() ->
                        log.debug("Broadcast Redis event stream cancelled for client: {}", clientId)
                )
                .doOnComplete(() ->
                        log.info("Broadcast Redis event stream completed for client: {}", clientId)
                );

        // Объединяем потоки
        return Flux.merge(pingFlux, eventFlux)
                .doOnCancel(() ->
                        log.info("⏹️ Message stream cancelled for client: {}", clientId)
                );
    }


    private Mono<Void> handleInboundMessages(String clientId, WebSocketSession session,
                                             AtomicBoolean isActive) {
        return session.receive()
                .doOnSubscribe(s ->
                        log.debug("📥 Starting inbound message handler for client: {}", clientId)
                )
                .doOnNext(wsMessage -> {
                    log.debug("📩 Received WebSocket message from client {}: {} bytes",
                            clientId, wsMessage.getPayloadAsText().length());
                    processClientMessage(clientId, wsMessage);
                })
                .doOnError(error -> {
                    if (isConnectionClosedError(error)) {
                        log.debug("Connection closed by client: {}", clientId);
                    } else {
                        log.error("❌ Error in inbound message handler for client {}: {}",
                                clientId, error.getMessage(), error);
                    }
                    isActive.set(false);
                })
                .doOnComplete(() -> {
                    log.info("✅ Inbound message stream completed for client: {}", clientId);
                    isActive.set(false);
                })
                .then();
    }

    private Mono<Void> handleOutboundMessages(String clientId, WebSocketSession session,
                                              AtomicBoolean isActive) {
        return session.send(
                        createMessageStream(clientId, isActive)
                                .takeWhile(ignore -> isActive.get() && session.isOpen())
                                .map(message -> convertToWebSocketMessage(session, message))
                                .doOnNext(wsMessage ->
                                        log.trace("📤 Preparing to send message to client: {}", clientId)
                                )
                                .doOnError(error -> {
                                    if (isConnectionClosedError(error)) {
                                        log.debug("Failed to send to disconnected client: {}", clientId);
                                    } else {
                                        log.error("❌ Error in outbound message stream for client {}: {}",
                                                clientId, error.getMessage(), error);
                                    }
                                    isActive.set(false);
                                })
                                .doOnComplete(() ->
                                        log.info("✅ Outbound message stream completed for client: {}", clientId)
                                )
                )
                .doOnError(error -> {
                    if (isConnectionClosedError(error)) {
                        log.debug("Error sending to disconnected client: {}", clientId);
                    } else {
                        log.error("❌ Error sending messages to client {}: {}",
                                clientId, error.getMessage(), error);
                    }
                    isActive.set(false);
                });
    }

//    private Flux<WebSocketMessageDTO> createMessageStream(String clientId, AtomicBoolean isActive) {
//        // Ping сообщения
//        Flux<WebSocketMessageDTO> pingFlux = Flux.interval(PING_INTERVAL)
//                .takeWhile(tick -> isActive.get())
//                .map(tick -> {
//                    log.trace("🔄 Sending PING to client: {}", clientId);
//                    return WebSocketMessageDTO.builder()
//                            .type(WebSocketMessageDTO.MessageType.PING)
//                            .timestamp(Instant.now().toEpochMilli())
//                            .serverTime(System.currentTimeMillis())
//                            .build();
//                });
//
//        // События из Redis Stream
//        Flux<WebSocketMessageDTO> eventFlux = redisStreamConsumerService.consumeEventsLatestOnly(clientId)
//                .takeWhile(ignore -> isActive.get() && isActive.get()) // Двойная проверка для безопасности
//                .doOnSubscribe(s ->
//                        log.info("📡 Starting Redis event consumption for client: {}", clientId)
//                )
//                .doOnNext(event ->
//                        log.debug("📨 Sending event to client {}: {}",
//                                clientId, event.getEvent().getEventType())
//                )
//                .doOnError(error -> {
//                    if (!isConnectionClosedError(error)) {
//                        log.error("❌ Error in Redis event stream for client {}: {}",
//                                clientId, error.getMessage(), error);
//                    }
//                })
//                .doOnCancel(() ->
//                        log.debug("Redis event stream cancelled for client: {}", clientId)
//                )
//                .doOnComplete(() ->
//                        log.info("Redis event stream completed for client: {}", clientId)
//                );
//
//        // Объединяем потоки
//        return Flux.merge(pingFlux, eventFlux)
//                .doOnCancel(() ->
//                        log.info("⏹️ Message stream cancelled for client: {}", clientId)
//                );
//    }

    private void processClientMessage(String clientId,
                                      org.springframework.web.reactive.socket.WebSocketMessage wsMessage) {
        try {
            String payload = wsMessage.getPayloadAsText();
            log.debug("Processing client message: {}", payload.substring(0, Math.min(100, payload.length())));

            WebSocketMessageDTO clientMessage = objectMapper.readValue(payload, WebSocketMessageDTO.class);

            log.info("📋 Processing {} message from client {}",
                    clientMessage.getType(), clientId);

            switch (clientMessage.getType()) {
                case PONG:
                    handlePongMessage(clientId);
                    break;
                case SUBSCRIPTION:
                    handleSubscriptionMessage(clientId, clientMessage);
                    break;
                case UNSUBSCRIPTION:
                    handleUnsubscriptionMessage(clientId, clientMessage);
                    break;
                default:
                    log.warn("⚠️ Unhandled message type from client {}: {}",
                            clientId, clientMessage.getType());
            }

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse message from client {}: {}", clientId, e.getMessage());
            sendErrorMessage(clientId, "Invalid message format: " + e.getMessage());
        } catch (Exception e) {
            log.error("💥 Error processing message from client {}: {}",
                    clientId, e.getMessage(), e);
            sendErrorMessage(clientId, "Internal server error");
        }
    }

    private void handlePongMessage(String clientId) {
        log.info("🏓 Received PONG from client: {}", clientId);
        sessionManager.updateLastActivity(clientId);
    }

    private void handleSubscriptionMessage(String clientId, WebSocketMessageDTO message) {
        String stationId = message.getStationId();
        if (stationId == null || stationId.isBlank()) {
            log.warn("⚠️ Client {} sent subscription without stationId", clientId);
            sendErrorMessage(clientId, "Station ID is required for subscription");
            return;
        }

        log.info("➕ Client {} subscribed to station: {}", clientId, stationId);
        sessionManager.addSubscription(clientId, stationId);

        // Отправляем подтверждение
        sendSubscriptionAck(clientId, stationId, true);
    }

    private void handleUnsubscriptionMessage(String clientId, WebSocketMessageDTO message) {
        String stationId = message.getStationId();
        if (stationId == null || stationId.isBlank()) {
            log.warn("⚠️ Client {} sent unsubscription without stationId", clientId);
            sendErrorMessage(clientId, "Station ID is required for unsubscription");
            return;
        }

        log.info("➖ Client {} unsubscribed from station: {}", clientId, stationId);
        sessionManager.removeSubscription(clientId, stationId);

        // Отправляем подтверждение
        sendSubscriptionAck(clientId, stationId, false);
    }

    private WebSocketMessage convertToWebSocketMessage(WebSocketSession session,
                                                       WebSocketMessageDTO messageDTO) {
        try {
            String json = objectMapper.writeValueAsString(messageDTO);
            log.trace("Serialized message: {}", json.substring(0, Math.min(100, json.length())));
            return session.textMessage(json);
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize message: {}", e.getMessage());
            return session.textMessage("{\"type\":\"ERROR\",\"message\":\"Serialization error\"}");
        }
    }

    private void sendErrorMessage(String clientId, String errorMessage) {
        try {
            WebSocketSession session = sessionManager.getSession(clientId);
            if (session != null && session.isOpen()) {
                WebSocketMessageDTO error = WebSocketMessageDTO.builder()
                        .type(WebSocketMessageDTO.MessageType.ERROR)
                        .message(errorMessage)
                        .timestamp(Instant.now().toEpochMilli())
                        .build();

                String json = objectMapper.writeValueAsString(error);
                session.send(Mono.just(session.textMessage(json)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnError(e ->
                                log.error("Failed to send error message to client {}: {}",
                                        clientId, e.getMessage())
                        )
                        .subscribe();
            }
        } catch (Exception e) {
            log.error("❌ Error sending error message to client {}: {}", clientId, e.getMessage());
        }
    }

    private void sendSubscriptionAck(String clientId, String stationId, boolean subscribed) {
        try {
            WebSocketSession session = sessionManager.getSession(clientId);
            if (session != null && session.isOpen()) {
                WebSocketMessageDTO ack = WebSocketMessageDTO.builder()
                        .type(subscribed ? WebSocketMessageDTO.MessageType.SUBSCRIPTION
                                : WebSocketMessageDTO.MessageType.UNSUBSCRIPTION)
                        .stationId(stationId)
                        .message(subscribed ? "Subscribed successfully" : "Unsubscribed successfully")
                        .timestamp(Instant.now().toEpochMilli())
                        .build();

                String json = objectMapper.writeValueAsString(ack);
                session.send(Mono.just(session.textMessage(json)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();

                log.debug("✅ Sent subscription ack to client {} for station {}",
                        clientId, stationId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to send subscription ack to client {}: {}",
                    clientId, e.getMessage());
        }
    }

    private void cleanupConnection(String clientId, AtomicBoolean isActive) {
        if (!isActive.getAndSet(false)) {
            return; // Уже очищено
        }

        log.info("🧹 Cleaning up connection for client: {}", clientId);

        // НЕ пытаемся подтвердить сообщения с некорректным ID
        // RedisStreamConsumerService теперь обрабатывает ACK асинхронно

        // Удаляем сессию из менеджера
        sessionManager.unregisterSession(clientId);
        sessionManager.unregisterUserSession(clientId);

        log.info("✅ Connection cleanup completed for client: {}", clientId);
    }

    private boolean isConnectionClosedError(Throwable throwable) {
        return throwable instanceof AbortedException ||
                throwable instanceof IOException ||
                (throwable.getMessage() != null &&
                        (throwable.getMessage().contains("Connection has been closed") ||
                                throwable.getMessage().contains("connection reset") ||
                                throwable.getMessage().contains("Broken pipe")));
    }
}