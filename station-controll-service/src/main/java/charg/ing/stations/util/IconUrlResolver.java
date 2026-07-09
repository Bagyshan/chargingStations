package charg.ing.stations.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Приводит сохранённый путь иконки коннектора к абсолютному публичному URL,
 * доступному снаружи (например {@code http://34.136.86.114/uploads/...}).
 *
 * <p>В базе иконка хранится относительным путём ({@code /uploads/connector-icons/x.jpg}).
 * Раньше {@code connectorTypeIcon} собирался из хоста входящего запроса, из-за чего
 * при вызове через api-gateway/WebClient в URL попадал внутренний docker-хост
 * {@code station-controll-service:8001}, недоступный клиентам. Этот резолвер
 * использует единый настраиваемый публичный базовый URL.</p>
 *
 * <p>Понимает любую форму входа: относительный путь, внутренний docker-URL и уже
 * публичный URL — во всех случаях выдаёт {@code {base}/uploads/...}. Если базовый
 * URL не задан ({@code app.public-base-url} пуст) — возвращает относительный путь
 * (старое поведение, безопасный дефолт для локальной разработки).</p>
 */
@Component
public class IconUrlResolver {

    private final String publicBaseUrl;

    public IconUrlResolver(@Value("${app.public-base-url:}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null
                ? ""
                : publicBaseUrl.trim().replaceAll("/+$", "");
    }

    /**
     * @param stored значение из БД/состояния (относительный путь, внутренний или публичный URL)
     * @return абсолютный публичный URL иконки, либо относительный путь, если база не настроена;
     *         {@code null}/пустое значение возвращается без изменений
     */
    public String resolve(String stored) {
        if (stored == null) {
            return null;
        }
        String s = stored.trim();
        if (s.isEmpty()) {
            return s;
        }
        int uploadsAt = s.indexOf("/uploads/");
        String relative = uploadsAt >= 0
                ? s.substring(uploadsAt)
                : (s.startsWith("/") ? s : "/" + s);
        return publicBaseUrl.isEmpty() ? relative : publicBaseUrl + relative;
    }
}
