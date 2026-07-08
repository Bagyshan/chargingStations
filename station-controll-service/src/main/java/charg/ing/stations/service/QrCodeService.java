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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private final StationRepository stationRepository;

    @Value("${qr.payload-base:batenergy://charge}")
    private String payloadBase;

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
                    .encodeToString(renderPng(buildPayload(chargeBoxId, c.getConnectorId()), 480));
            String typeName = c.getConnectorType() != null
                    ? c.getConnectorType().getConnectorTypeName()
                    : "";
            stickers.append("""
                    <div class="sticker">
                      <div class="head">
                        <div class="brand">⚡ BatEnergy</div>
                        <div class="conn">Коннектор №%d</div>
                      </div>
                      <img class="qr" src="data:image/png;base64,%s" alt="QR коннектора %d"/>
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
                    width: 90mm; padding: 6mm; background: #fff; border-radius: 5mm;
                    border: 1.2mm solid #111; text-align: center;
                    page-break-inside: avoid; break-inside: avoid;
                  }
                  .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4mm; }
                  .brand { font-weight: 800; font-size: 15pt; }
                  .conn { font-weight: 800; font-size: 12pt; background: #111; color: #fff;
                          border-radius: 99px; padding: 1.5mm 4mm; }
                  .qr { width: 70mm; height: 70mm; image-rendering: pixelated; }
                  .hint { font-size: 10pt; font-weight: 600; margin: 3mm 0 4mm; line-height: 1.35; }
                  .meta { border-top: 0.4mm solid #ccc; padding-top: 3mm; }
                  .station { font-weight: 700; font-size: 10pt; }
                  .addr { font-size: 9pt; color: #555; margin-top: 1mm; }
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
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M); // наклейки царапаются
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("QR encode failed for payload: " + payload, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
