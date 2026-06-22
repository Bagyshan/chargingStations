package charg.ing.stations.consumer;

import charg.ing.stations.entity.User;
import charg.ing.stations.entity.enums.UserRole;
import charg.ing.stations.event.StationAlertEvent;
import charg.ing.stations.event.UserEvent;
import charg.ing.stations.event.enums.UserEventType;
import charg.ing.stations.repository.UserRepository;
import charg.ing.stations.service.KafkaEventProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Слушает {@code station.alerts} (из station-controll) и рассылает алерт о неисправности станции:
 * владельцу станции (по {@code ownerId} = keycloakId) и всем пользователям с ролями ADMIN/SPECIALIST.
 * Для каждого получателя публикует {@link UserEventType#STATION_FAULTED} в {@code notification.events},
 * который рассылает notification-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StationAlertConsumer {

    private final UserRepository userRepository;
    private final KafkaEventProducer kafkaEventProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "station.alerts",
            groupId = "user-service-station-alerts",
            containerFactory = "kafkaListenerContainerFactory")
    public void onStationAlert(String value) {
        StationAlertEvent alert;
        try {
            alert = objectMapper.readValue(value, StationAlertEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse station alert: {}", value, e);
            return;
        }
        log.info("Station alert for {} (connector {}, status {}) — resolving recipients",
                alert.getChargeBoxId(), alert.getConnectorId(), alert.getStatus());

        resolveRecipients(alert)
                .flatMap(user -> kafkaEventProducer.sendNotificationEvent(buildEvent(user, alert)))
                .doOnError(e -> log.error("Failed to fan out station alert for {}", alert.getChargeBoxId(), e))
                .subscribe();
    }

    /** Владелец станции (по keycloakId) + все ADMIN + все SPECIALIST, дедуп по email. */
    private Flux<User> resolveRecipients(StationAlertEvent alert) {
        Flux<User> owner = StringUtils.hasText(alert.getOwnerId())
                ? userRepository.findByKeycloakId(alert.getOwnerId()).flux()
                : Flux.empty();
        Flux<User> admins = userRepository.findByRole(UserRole.ADMIN);
        Flux<User> specialists = userRepository.findByRole(UserRole.SPECIALIST);

        return Flux.concat(owner, admins, specialists)
                .filter(u -> u != null && StringUtils.hasText(u.getEmail()))
                .distinct(User::getEmail);
    }

    private UserEvent buildEvent(User user, StationAlertEvent alert) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chargeBoxId", alert.getChargeBoxId());
        metadata.put("connectorId", alert.getConnectorId());
        metadata.put("status", alert.getStatus());
        metadata.put("errorCode", alert.getErrorCode());

        return UserEvent.builder()
                .eventType(UserEventType.STATION_FAULTED)
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userRole(user.getRole() != null ? user.getRole().name() : null)
                .timestamp(LocalDateTime.now())
                .metadata(metadata)
                .build();
    }
}
