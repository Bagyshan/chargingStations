package charg.ing.stations.websocketservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@Slf4j
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActivity = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Instant> connectionTime = new ConcurrentHashMap<>();
    private final Map<String, String> userToClientId = new ConcurrentHashMap<>();
    private final Map<String, String> clientIdToUserId = new ConcurrentHashMap<>();


    public void registerUserSession(String userId, String clientId) {
        userToClientId.put(userId, clientId);
        clientIdToUserId.put(clientId, userId);
    }

    public void unregisterUserSession(String clientId) {
        String userId = clientIdToUserId.remove(clientId);
        if (userId != null) {
            userToClientId.remove(userId);
        }
    }

    public String getClientIdByUserId(String userId) {
        return userToClientId.get(userId);
    }

    public String getUserIdByClientId(String clientId) {
        return clientIdToUserId.get(clientId);
    }


    /**
     * Регистрирует новую WebSocket сессию
     */
    public void registerSession(String clientId, WebSocketSession session) {
        Instant now = Instant.now();
        sessions.put(clientId, session);
        lastActivity.put(clientId, now);
        connectionTime.put(clientId, now);
        subscriptions.put(clientId, new CopyOnWriteArraySet<>());

        log.info("Session registered for client: {} (total: {})",
                clientId, sessions.size());
    }

    /**
     * Удаляет WebSocket сессию
     */
    public void unregisterSession(String clientId) {
        WebSocketSession removed = sessions.remove(clientId);
        lastActivity.remove(clientId);
        connectionTime.remove(clientId);
        subscriptions.remove(clientId);

        if (removed != null) {
            log.info("Session unregistered for client: {} (total: {})",
                    clientId, sessions.size());
        }
    }

    /**
     * Обновляет время последней активности
     */
    public void updateLastActivity(String clientId) {
        lastActivity.put(clientId, Instant.now());
    }

    /**
     * Добавляет подписку на станцию
     */
    public void addSubscription(String clientId, String stationId) {
        subscriptions.computeIfAbsent(clientId, k -> new CopyOnWriteArraySet<>())
                .add(stationId);

        log.debug("Client {} subscribed to station {} (total subscriptions: {})",
                clientId, stationId, getSubscriptionCount(clientId));
    }

    /**
     * Удаляет подписку на станцию
     */
    public void removeSubscription(String clientId, String stationId) {
        Set<String> clientSubscriptions = subscriptions.get(clientId);
        if (clientSubscriptions != null) {
            boolean removed = clientSubscriptions.remove(stationId);
            if (removed) {
                log.debug("Client {} unsubscribed from station {} (remaining: {})",
                        clientId, stationId, clientSubscriptions.size());
            }
        }
    }

    /**
     * Проверяет, подписан ли клиент на станцию
     */
    public boolean isSubscribed(String clientId, String stationId) {
        Set<String> clientSubscriptions = subscriptions.get(clientId);
        return clientSubscriptions != null && clientSubscriptions.contains(stationId);
    }

    /**
     * Получает все подписки клиента
     */
    public Set<String> getSubscriptions(String clientId) {
        return subscriptions.getOrDefault(clientId, Set.of());
    }

    /**
     * Получает количество подписок клиента
     */
    public int getSubscriptionCount(String clientId) {
        Set<String> clientSubscriptions = subscriptions.get(clientId);
        return clientSubscriptions != null ? clientSubscriptions.size() : 0;
    }

    /**
     * Получает WebSocket сессию
     */
    public WebSocketSession getSession(String clientId) {
        return sessions.get(clientId);
    }

    /**
     * Проверяет, активна ли сессия
     */
    public boolean isSessionActive(String clientId) {
        WebSocketSession session = sessions.get(clientId);
        return session != null && session.isOpen();
    }

    /**
     * Получает количество активных сессий
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Получает время соединения
     */
    public Instant getConnectionTime(String clientId) {
        return connectionTime.get(clientId);
    }

    /**
     * Получает время последней активности
     */
    public Instant getLastActivity(String clientId) {
        return lastActivity.get(clientId);
    }

    /**
     * Получает список всех активных клиентов
     */
    public List<Map<String, Object>> getActiveClientsList() {
        List<Map<String, Object>> clients = new ArrayList<>();

        sessions.forEach((clientId, session) -> {
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("clientId", clientId);
            clientInfo.put("sessionId", session.getId());
            clientInfo.put("connectedSince", connectionTime.get(clientId));
            clientInfo.put("lastActivity", lastActivity.get(clientId));
            clientInfo.put("subscriptionCount", getSubscriptionCount(clientId));
            clientInfo.put("subscriptions", getSubscriptions(clientId));
            clientInfo.put("remoteAddress", session.getHandshakeInfo().getRemoteAddress());
            clientInfo.put("sessionOpen", session.isOpen());

            // Вычисляем время активности
            Instant lastActive = lastActivity.get(clientId);
            if (lastActive != null) {
                clientInfo.put("inactiveSeconds", Duration.between(lastActive, Instant.now()).getSeconds());
            }

            clients.add(clientInfo);
        });

        return clients;
    }

    /**
     * Очищает неактивные сессии
     */
    public void cleanupInactiveSessions(Duration maxInactivity) {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> {
            String clientId = entry.getKey();
            Instant lastActive = lastActivity.get(clientId);

            if (lastActive != null && lastActive.plus(maxInactivity).isBefore(now)) {
                WebSocketSession session = entry.getValue();
                if (session != null && session.isOpen()) {
                    try {
                        session.close().subscribe(
                                null,
                                error -> log.warn("Error closing inactive session for client {}: {}",
                                        clientId, error.getMessage())
                        );
                    } catch (Exception e) {
                        log.warn("Error closing inactive session for client {}: {}",
                                clientId, e.getMessage());
                    }
                }

                log.info("Cleaning up inactive session for client: {} (inactive for: {} seconds)",
                        clientId, Duration.between(lastActive, now).getSeconds());

                sessions.remove(clientId);
                lastActivity.remove(clientId);
                connectionTime.remove(clientId);
                subscriptions.remove(clientId);
                return true;
            }
            return false;
        });
    }
}