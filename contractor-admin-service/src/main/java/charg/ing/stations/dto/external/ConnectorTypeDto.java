package charg.ing.stations.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorTypeDto {
    private Integer id;
    private String connectorTypeName;
    /** Стабильный код типа (CCS2, TYPE2, …) — ключ к встроенной иконке. */
    private String code;
    /** Встроенная векторная иконка типа как SVG-текст (рисуется без файлов-картинок). */
    private String iconSvg;
    /** Устаревшее: URL загруженной картинки. Оставлено для обратной совместимости. */
    private String connectorTypeIcon;
    private Integer connectorsCount;
}
