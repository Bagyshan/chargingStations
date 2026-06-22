//package charg.ing.stations.controller;
//
//import charg.ing.stations.dto.StartTransactionCreateEvent;
//import charg.ing.stations.dto.StopTransactionUpdateEvent;
//import charg.ing.stations.enums.TransactionStatus;
//import charg.ing.stations.util.EmailValidator;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.server.ResponseStatusException;
//
//import java.time.Instant;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/transactions")
//@RequiredArgsConstructor
//public class TransactionController {
//
//    private final KafkaTemplate<String, Object> kafkaTemplate;
//    private final ObjectMapper objectMapper;
//
//    @Value("${kafka.topic.ocpp-requests:ocpp.requests}")
//    private String ocppRequestsTopic;
//
//    /**
//     * Запрос на старт транзакции
//     */
//    @PostMapping("/start")
//    public ResponseEntity<Void> startTransaction(
//            @RequestBody StartStopRequest request,
//            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
//
//        // 1. Извлечение и парсинг JWT
//        String token = extractToken(authHeader);
//        Map<String, Object> claims = parseJwt(token);  // упрощённо
//
//        String email = (String) claims.get("email");
//        String userId = (String) claims.get("sub");
//
//        // 2. Валидация email
//        if (!EmailValidator.isValid(email)) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
//        }
//
//        // 3. Формирование события
//        StartTransactionCreateEvent event = new StartTransactionCreateEvent();
//        event.setChargeBoxId(request.getChargeBoxId());
//        event.setConnectorId(request.getConnectorId());
//        // transactionId может быть сгенерирован позже, пока null
//        event.setStartTimestamp(Instant.now());
//        // startValue будет определён потребителем
//        event.setStatus(TransactionStatus.ACTIVE);  // предположительно
//        event.setUserId(userId);
//
//        // 4. Отправка в Kafka с ключом = chargeBoxId
//        kafkaTemplate.send(ocppRequestsTopic, request.getChargeBoxId(), event);
//
//        return ResponseEntity.accepted().build();
//    }
//
//    /**
//     * Запрос на остановку транзакции
//     */
//    @PostMapping("/stop")
//    public ResponseEntity<Void> stopTransaction(
//            @RequestBody StartStopRequest request,
//            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
//
//        String token = extractToken(authHeader);
//        Map<String, Object> claims = parseJwt(token);
//
//        String email = (String) claims.get("email");
//        String userId = (String) claims.get("sub");
//
//        if (!EmailValidator.isValid(email)) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
//        }
//
//        StopTransactionUpdateEvent event = new StopTransactionUpdateEvent();
//        event.setChargeBoxId(request.getChargeBoxId());
//        event.setConnectorId(request.getConnectorId());
//        // transactionId должен быть известен, но здесь он не передан.
//        // Возможно, нужно сначала найти активную транзакцию по chargeBoxId+connectorId.
//        // Для простоты предположим, что transactionId будет определён потребителем.
//        event.setStopTimestamp(Instant.now());
//        event.setStopValue("0");  // будет заменено реальным значением
//        event.setStatus(TransactionStatus.COMPLETED);
//        event.setUserId(userId);
//
//        kafkaTemplate.send(ocppRequestsTopic, request.getChargeBoxId(), event);
//
//        return ResponseEntity.accepted().build();
//    }
//
//    /**
//     * Извлечение токена из заголовка "Bearer ..."
//     */
//    private String extractToken(String authHeader) {
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
//        }
//        return authHeader.substring(7);
//    }
//
//    /**
//     * Упрощённый парсинг JWT без проверки подписи (для демо).
//     * В реальном проекте используйте JwtDecoder от Spring Security.
//     */
//    private Map<String, Object> parseJwt(String token) {
//        try {
//            String[] chunks = token.split("\\.");
//            if (chunks.length < 2) {
//                throw new IllegalArgumentException("Invalid JWT");
//            }
//            String payload = new String(java.util.Base64.getUrlDecoder().decode(chunks[1]));
//            return objectMapper.readValue(payload, Map.class);
//        } catch (Exception e) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT", e);
//        }
//    }
//
//    /**
//     * Внутренний класс для тела запроса
//     */
//    public static class StartStopRequest {
//        private String chargeBoxId;
//        private Integer connectorId;
//        private String ocppTag;  // может использоваться для идентификации, но пока не нужно
//
//        // геттеры и сеттеры
//        public String getChargeBoxId() { return chargeBoxId; }
//        public void setChargeBoxId(String chargeBoxId) { this.chargeBoxId = chargeBoxId; }
//        public Integer getConnectorId() { return connectorId; }
//        public void setConnectorId(Integer connectorId) { this.connectorId = connectorId; }
//        public String getOcppTag() { return ocppTag; }
//        public void setOcppTag(String ocppTag) { this.ocppTag = ocppTag; }
//    }
//}