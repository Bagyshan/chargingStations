package charg.ing.stations.util;

/**
 * Нормализует имя типа коннектора в стабильный машинный код
 * ({@code CCS2}, {@code TYPE2}, {@code CHADEMO}, …). Код — контракт между
 * бэкендом, мобильным приложением и админкой: по нему каждый клиент
 * связывает коннектор со своей встроенной векторной иконкой.
 *
 * <p>Логика повторяет клиентский нормализатор (Dart {@code ConnectorCodes.fromName}),
 * чтобы код совпадал независимо от того, кто его вычислил. Порядок проверок
 * важен: CCS раньше Type, т.к. в «CCS Combo 2» встречается «Type 2».</p>
 */
public final class ConnectorTypeCodes {

    private ConnectorTypeCodes() {
    }

    public static String fromName(String name) {
        String n = (name == null ? "" : name).toLowerCase().replaceAll("[\\s\\-_/.]", "");
        if (n.isEmpty()) return "OTHER";
        if (n.contains("chademo")) return "CHADEMO";
        if (n.contains("nacs") || n.contains("tesla") || n.contains("supercharger")) return "NACS";
        if (n.contains("ccs") || n.contains("combo")) {
            return (n.contains("combo1") || n.contains("ccs1")) ? "CCS1" : "CCS2";
        }
        if (n.contains("gbt") || n.contains("guobiao")) {
            return n.contains("dc") ? "GBT_DC" : "GBT";
        }
        if (n.contains("type2") || n.contains("mennekes")) return "TYPE2";
        if (n.contains("type1") || n.contains("j1772") || n.contains("sae")) return "TYPE1";
        return "OTHER";
    }
}
