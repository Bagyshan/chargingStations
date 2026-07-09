package charg.ing.stations.dto.connector_type;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectorTypeResponse {
    private Integer id;
    private String connectorTypeName;
    /** Стабильный код типа (CCS2, TYPE2, …) — ключ к встроенной иконке у клиентов. */
    private String code;
    /** Встроенная векторная иконка как SVG-текст (тот же набор, что в приложении). */
    private String iconSvg;
    /** Устаревшее: URL загруженной картинки. Оставлено для обратной совместимости. */
    private String connectorTypeIcon;
    private Integer connectorsCount;
}