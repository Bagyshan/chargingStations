package charg.ing.stations.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отдаёт встроенную векторную иконку типа коннектора как SVG-текст по коду.
 *
 * <p>Иконки лежат в classpath ({@code resources/connector-icons/*.svg}) — это тот
 * же канонический набор, что встроен в мобильное приложение как ассеты. Никаких
 * загружаемых файлов/томов: SVG — часть артефакта, отдаётся строкой в
 * {@code connector-types}, чтобы админка рисовала ровно те же иконки, что и
 * приложение, без хранения картинок.</p>
 */
@Component
@Slf4j
public class ConnectorIconProvider {

    private static final Map<String, String> FILE_BY_CODE = Map.of(
            "TYPE1", "type1.svg",
            "TYPE2", "type2.svg",
            "CCS1", "ccs1.svg",
            "CCS2", "ccs2.svg",
            "CHADEMO", "chademo.svg",
            "GBT", "gbt.svg",
            "GBT_DC", "gbt.svg",
            "NACS", "nacs.svg"
    );
    private static final String GENERIC = "generic.svg";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** SVG-разметка иконки по коду; для неизвестного кода — универсальная иконка. */
    public String svgForCode(String code) {
        String file = FILE_BY_CODE.getOrDefault(
                code == null ? "" : code.toUpperCase(), GENERIC);
        return cache.computeIfAbsent(file, this::load);
    }

    private String load(String file) {
        ClassPathResource resource = new ClassPathResource("connector-icons/" + file);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Connector icon resource not found: {}", file);
            return null;
        }
    }
}
