package charg.ing.stations.websocketservice.dto;

import charg.ing.stations.websocketservice.dto.booking.BookingEventDTO;
import charg.ing.stations.websocketservice.dto.charging.ChargingStatusDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessageDTO {

    public enum MessageType {
        EVENT,          // Событие изменения станции
        PING,           // Ping сообщение
        PONG,           // Pong ответ
        SUBSCRIPTION,   // Подписка на станции
        UNSUBSCRIPTION, // Отписка от станций
        ERROR,          // Ошибка
        SUBSCRIPTION_ACK,
        UNSUBSCRIPTION_ACK,
    }

    private MessageType type;
    private StationEventDTO event;
    private BookingEventDTO bookingEvent;
    private ChargingStatusDTO chargingEvent;
    private String subscriptionId;
    private String stationId;
    private String message;
    private Long timestamp;

    @Builder.Default
    private Long serverTime = System.currentTimeMillis();
}