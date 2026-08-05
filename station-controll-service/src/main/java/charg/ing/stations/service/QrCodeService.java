package charg.ing.stations.service;

import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.repository.StationRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Генерация QR-наклеек для станций. Наклейка клеится на каждый коннектор (пистолет):
 * скан в мобильном приложении сразу открывает подтверждение зарядки нужного коннектора.
 * <p>
 * Payload — deep link вида {@code batenergy://charge?station={chargeBoxId}&connector={connectorId}},
 * база настраивается свойством {@code qr.payload-base}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private final StationRepository stationRepository;

    @Value("${qr.payload-base:batenergy://charge}")
    private String payloadBase;

    /** Логотип приложения для наложения в центр QR (грузится один раз из ресурсов). */
    private final BufferedImage logo = loadLogo();

    private static BufferedImage loadLogo() {
        try (InputStream in = QrCodeService.class.getResourceAsStream("/qr/logo.png")) {
            return in != null ? ImageIO.read(in) : null;
        } catch (IOException e) {
            return null; // без логотипа QR всё равно генерируется
        }
    }

    public String buildPayload(String chargeBoxId, int connectorId) {
        return payloadBase + "?station=" + chargeBoxId + "&connector=" + connectorId;
    }

    /** PNG QR-кода одного коннектора. Станция и коннектор должны существовать в каталоге. */
    public byte[] connectorQrPng(String chargeBoxId, int connectorId, int sizePx) {
        ChargeBoxEntity station = requireStation(chargeBoxId);
        requireConnector(station, connectorId);
        return renderPng(buildPayload(chargeBoxId, connectorId), sizePx);
    }

    /**
     * Печатный HTML-лист со всеми наклейками станции (по одной на коннектор).
     * QR встроены как base64 data-URI — лист самодостаточен, открывается в браузере и печатается.
     */
    public String stickerSheetHtml(String chargeBoxId) {
        ChargeBoxEntity station = requireStation(chargeBoxId);
        List<ConnectorEntity> connectors = station.getConnectors().stream()
                .filter(c -> c.getConnectorId() > 0) // connector 0 в OCPP — вся станция, наклейка не нужна
                .sorted(Comparator.comparingInt(ConnectorEntity::getConnectorId))
                .toList();
        if (connectors.isEmpty()) {
            throw new IllegalStateException("Station has no connectors: " + chargeBoxId);
        }

        String address = station.getAddress() != null ? station.getAddress().getAddressName() : "";

        StringBuilder stickers = new StringBuilder();
        for (ConnectorEntity c : connectors) {
            String qrBase64 = Base64.getEncoder()
                    .encodeToString(renderPng(buildPayload(chargeBoxId, c.getConnectorId()), 640));
            String typeName = c.getConnectorType() != null
                    ? c.getConnectorType().getConnectorTypeName()
                    : "";
            stickers.append("""
                    <div class="sticker">
                      <div class="head">
                        <div class="brand">⚡ BatEnergy</div>
                        <div class="conn">Коннектор №%d</div>
                      </div>
                      <div class="qrwrap"><img class="qr" src="data:image/png;base64,%s" alt="QR коннектора %d"/></div>
                      <div class="hint">Отсканируйте QR-код в приложении BatEnergy,<br/>чтобы начать зарядку</div>
                      <div class="meta">
                        <div class="station">%s%s</div>
                        <div class="addr">%s</div>
                      </div>
                    </div>
                    """.formatted(
                    c.getConnectorId(),
                    qrBase64,
                    c.getConnectorId(),
                    escapeHtml(chargeBoxId),
                    typeName.isBlank() ? "" : " · " + escapeHtml(typeName),
                    escapeHtml(address)));
        }

        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                <meta charset="utf-8"/>
                <title>QR-наклейки · %s</title>
                <style>
                  * { box-sizing: border-box; margin: 0; padding: 0; }
                  body { font-family: Arial, Helvetica, sans-serif; background: #f2f2f2; padding: 10mm; }
                  .sheet { display: flex; flex-wrap: wrap; gap: 8mm; }
                  .sticker {
                    width: 92mm; padding: 6mm; border-radius: 6mm; text-align: center; color: #fff;
                    background: linear-gradient(135deg, #FFB43A, #FFA20D, #8E4368, #5A2E5C);
                    page-break-inside: avoid; break-inside: avoid;
                    -webkit-print-color-adjust: exact; print-color-adjust: exact;
                  }
                  .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4mm; }
                  .brand { font-weight: 800; font-size: 15pt; color: #fff; }
                  .conn { font-weight: 800; font-size: 12pt; background: rgba(255,255,255,0.24); color: #fff;
                          border-radius: 99px; padding: 1.5mm 4mm; }
                  .qrwrap { display: inline-block; background: #fff; border-radius: 4mm; padding: 4mm; line-height: 0; }
                  .qr { width: 72mm; height: 72mm; image-rendering: pixelated; display: block; }
                  .hint { font-size: 10.5pt; font-weight: 700; margin: 4mm 0 4mm; line-height: 1.35; color: #fff; }
                  .meta { border-top: 0.4mm solid rgba(255,255,255,0.42); padding-top: 3mm; }
                  .station { font-weight: 800; font-size: 10pt; color: #fff; }
                  .addr { font-size: 9pt; color: rgba(255,255,255,0.85); margin-top: 1mm; }
                  @media print { body { background: #fff; padding: 0; } }
                </style>
                </head>
                <body>
                <div class="sheet">
                %s</div>
                </body>
                </html>
                """.formatted(escapeHtml(chargeBoxId), stickers);
    }

    private ChargeBoxEntity requireStation(String chargeBoxId) {
        return stationRepository.findByChargeBoxId(chargeBoxId)
                .orElseThrow(() -> new IllegalStateException("Station not found: " + chargeBoxId));
    }

    private void requireConnector(ChargeBoxEntity station, int connectorId) {
        boolean exists = station.getConnectors().stream()
                .anyMatch(c -> c.getConnectorId() == connectorId);
        if (!exists) {
            throw new IllegalStateException(
                    "Connector " + connectorId + " not found on station " + station.getChargeBoxId());
        }
    }

    private byte[] renderPng(String payload, int sizePx) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        // H = 30% коррекции: и наклейки царапаются, и можно наложить логотип в центр
        // без потери читаемости сканером.
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);
            overlayLogo(qr, sizePx);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(qr, "PNG", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("QR encode failed for payload: " + payload, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Логотип приложения в центре QR — на белой скруглённой подложке (контраст + отделение от модулей). */
    private void overlayLogo(BufferedImage qr, int sizePx) {
        if (logo == null) {
            return;
        }
        int logoSize = Math.round(sizePx * 0.22f);
        int backSize = Math.round(sizePx * 0.28f);
        int cx = sizePx / 2;
        int cy = sizePx / 2;
        Graphics2D g = qr.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        float backArc = backSize * 0.32f;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(cx - backSize / 2f, cy - backSize / 2f, backSize, backSize, backArc, backArc));

        float logoArc = logoSize * 0.30f;
        Shape prevClip = g.getClip();
        g.setClip(new RoundRectangle2D.Float(cx - logoSize / 2f, cy - logoSize / 2f, logoSize, logoSize, logoArc, logoArc));
        g.drawImage(logo, cx - logoSize / 2, cy - logoSize / 2, logoSize, logoSize, null);
        g.setClip(prevClip);
        g.dispose();
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
