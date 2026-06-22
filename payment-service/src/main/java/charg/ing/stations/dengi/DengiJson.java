package charg.ing.stations.dengi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Dedicated ObjectMapper for O!Dengi request serialization + hash computation, so the signed bytes
 * match the sent bytes exactly: non-ASCII unescaped (like PHP json_encode JSON_UNESCAPED_UNICODE),
 * no pretty-printing.
 *
 * <p>Intentionally NOT a Spring bean: declaring an {@code ObjectMapper} bean makes Spring Boot back
 * off its auto-configured (JSR-310-aware) mapper, which would then break WebFlux response encoding
 * of {@code java.time.*} types.
 */
public final class DengiJson {

    public static final ObjectMapper MAPPER = create();

    private DengiJson() {}

    private static ObjectMapper create() {
        ObjectMapper m = new ObjectMapper();
        m.disable(SerializationFeature.INDENT_OUTPUT);
        m.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
        return m;
    }
}
