package charg.ing.stations.controller;

import charg.ing.stations.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * QR-наклейки станций. GET /api/stations/** — публичный (см. SecurityConfig):
 * QR несёт только chargeBoxId и номер коннектора, секретов в нём нет.
 */
@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class QrCodeController {

    private final QrCodeService qrCodeService;

    /** PNG QR-кода одного коннектора (для проверки/своей вёрстки наклейки). */
    @GetMapping(value = "/{chargeBoxId}/qr/{connectorId}", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<byte[]>> connectorQr(
            @PathVariable String chargeBoxId,
            @PathVariable int connectorId,
            @RequestParam(defaultValue = "480") int size
    ) {
        int sizePx = Math.max(120, Math.min(size, 2000));
        // Блокирующее JPA-чтение — вне event-loop WebFlux (как в StationController).
        return Mono.fromCallable(() -> qrCodeService.connectorQrPng(chargeBoxId, connectorId, sizePx))
                .subscribeOn(Schedulers.boundedElastic())
                .map(png -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" + chargeBoxId + "-connector-" + connectorId + ".png\"")
                        .contentType(MediaType.IMAGE_PNG)
                        .body(png))
                .onErrorResume(IllegalStateException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    /** Печатный лист со всеми наклейками станции — открыть в браузере и распечатать. */
    @GetMapping(value = "/{chargeBoxId}/qr-sheet", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> stickerSheet(@PathVariable String chargeBoxId) {
        return Mono.fromCallable(() -> qrCodeService.stickerSheetHtml(chargeBoxId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(html -> ResponseEntity.ok()
                        .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                        .body(html))
                .onErrorResume(IllegalStateException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }
}
