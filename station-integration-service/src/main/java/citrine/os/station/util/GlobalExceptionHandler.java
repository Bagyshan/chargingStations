package citrine.os.station.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//@RestControllerAdvice
//public class GlobalExceptionHandler {
//
//    @ExceptionHandler(HttpMessageNotReadableException.class)
//    public ResponseEntity<String> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
//        return ResponseEntity
//                .status(HttpStatus.BAD_REQUEST)
//                .body("Ошибка десериализации JSON: " + ex.getMostSpecificCause().getMessage());
//    }
//}