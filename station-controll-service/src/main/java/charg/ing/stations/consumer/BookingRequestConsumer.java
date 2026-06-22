package charg.ing.stations.consumer;


import charg.ing.stations.dto.availability.AvailabilityResult;
import charg.ing.stations.dto.kafka.StationRequest;
import charg.ing.stations.dto.kafka.StationResponse;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.service.StationAvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingRequestConsumer {

    private final ChargeBoxRepository chargeBoxRepository;
    private final StationAvailabilityService availabilityService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "booking.station.requests", groupId = "station-controller-service-group")
    @Transactional
    public void handleStationRequest(Map<String, Object> messageMap) {
        log.info("Received raw station message map: {}", messageMap);

        final StationRequest request;
        try {
            request = convertMapToStationRequest(messageMap);
        } catch (Exception e) {
            log.error("Failed to parse StationRequest from map: {}", messageMap, e);
            // Если не удалось распарсить, отправляем ответ об ошибке
            // и прекращаем обработку
            sendErrorResponseForMalformedMessage(messageMap, "Invalid message format");
            return;
        }

        log.info("Successfully parsed station request: {}", request);

        // 1-3. Единая проверка доступности: станция существует и в работе, коннектор существует,
        //       Available и не забронирован. Сломанные/выключенные станции отсекаются здесь.
        AvailabilityResult availability =
                availabilityService.checkBookable(request.getStationId(), request.getConnectorId());
        if (!availability.available()) {
            log.info("Station {} connector {} not bookable: {} ({})",
                    request.getStationId(), request.getConnectorId(),
                    availability.reason(), availability.message());
            sendErrorResponse(request, availability.message());
            return;
        }

        ChargeBoxEntity chargeBox = chargeBoxRepository.findByChargeBoxId(request.getStationId());

        // 4. Получаем стоимость минуты (поле должно быть добавлено)
        BigDecimal pricePerMinute = chargeBox.getBookingMinuteCost();
        if (pricePerMinute == null) {
            // На случай отсутствия цены – можно взять значение по умолчанию или вернуть ошибку
            pricePerMinute = BigDecimal.ZERO;
            log.warn("Booking minute cost is null for station {}, using 0", request.getStationId());
        }

        // 5. Формируем успешный ответ
        StationResponse response = new StationResponse(
                request.getRequestId(),
                request.getStationId(),
                request.getConnectorId(),
                pricePerMinute,
                true,
                null
        );

        sendResponse(response);
    }


    /**
     * Вспомогательный метод для конвертации Map в StationRequest.
     */
    private StationRequest convertMapToStationRequest(Map<String, Object> map) throws IllegalArgumentException {
        Object requestIdObj = map.get("requestId");
        Object stationIdObj = map.get("stationId");
        Object connectorIdObj = map.get("connectorId");

        if (requestIdObj == null || stationIdObj == null || connectorIdObj == null) {
            throw new IllegalArgumentException("Map is missing required fields: " + map.keySet());
        }

        try {
            UUID requestId = UUID.fromString(requestIdObj.toString());
            String stationId = stationIdObj.toString();
            Integer connectorId = Integer.parseInt(connectorIdObj.toString());
            return new StationRequest(requestId, stationId, connectorId);
        } catch (ClassCastException | NumberFormatException e) {
            throw new IllegalArgumentException("Failed to convert map fields to correct types for StationRequest", e);
        }
    }

    /**
     * Отправка ответа об ошибке, когда не удалось распарсить сам запрос.
     */
    private void sendErrorResponseForMalformedMessage(Map<String, Object> map, String errorMessage) {
        UUID requestId = null;
        try {
            // Пытаемся все-таки вытащить requestId для ответа
            if (map.containsKey("requestId")) {
                requestId = UUID.fromString(map.get("requestId").toString());
            }
        } catch (Exception e) {
            log.error("CRITICAL: Cannot extract requestId from malformed message, cannot send a proper error response.");
        }

        if (requestId != null) {
            StationResponse response = new StationResponse(
                    requestId,
                    null, // stationId неизвестен
                    null, // connectorId неизвестен
                    null,
                    false,
                    errorMessage
            );
            sendResponse(response);
        }
    }

    private void sendErrorResponse(StationRequest request, String errorMessage) {
        StationResponse response = new StationResponse(
                request.getRequestId(),
                request.getStationId(),
                request.getConnectorId(),
                null,
                false,
                errorMessage
        );
        sendResponse(response);
    }

    private void sendResponse(StationResponse response) {
        kafkaTemplate.send("booking.station.responses", response.getRequestId().toString(), response);
        log.info("Sent station response: {}", response);
    }
}