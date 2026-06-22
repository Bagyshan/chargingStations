package charg.ing.stations.controller.exception_handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
//
//@ControllerAdvice(annotations = RestController.class)
//public class GlobalExceptionHandler {
//
//    // Обрабатываем конкретные ошибки, например, ошибки валидации
//    @ExceptionHandler(IllegalArgumentException.class)
//    public Mono<ResponseEntity<Map<String, String>>> handleIllegalArgument(IllegalArgumentException e) {
//        return Mono.just(ResponseEntity.badRequest().body(Map.of("error", e.getMessage())));
//    }
//
//    // Обрабатываем все остальные непредвиденные ошибки
//    @ExceptionHandler(Exception.class)
//    public Mono<ResponseEntity<Map<String, String>>> handleGeneralException(Exception e) {
//        // Здесь лучше использовать логгер
//        // log.error("An unexpected error occurred", e);
//        return Mono.just(ResponseEntity.internalServerError().body(Map.of("error", "Internal server error")));
//    }
//}
