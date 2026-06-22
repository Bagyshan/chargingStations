package charg.ing.stations.dengi;

import charg.ing.stations.config.DengiProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DengiSignatureServiceTest {

    private static ObjectMapper dengiMapper() {
        ObjectMapper m = new ObjectMapper();
        m.disable(SerializationFeature.INDENT_OUTPUT);
        m.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
        return m;
    }

    private static DengiSignatureService signerWithPassword(String password) {
        DengiProperties props = new DengiProperties();
        props.setPassword(password);
        return new DengiSignatureService(props);
    }

    /** Documented O!Dengi vector: HMAC-MD5(key=password, message=full request JSON without hash). */
    @Test
    void signRequestJson_matchesDocumentationVector() {
        DengiSignatureService signer = signerWithPassword("Z@(K0APS@B~MW1Q");
        String json = "{\"cmd\":\"getOTP\",\"version\":1005,\"sid\":\"1000000001\","
                + "\"mktime\":\"1487602271287\",\"lang\":\"ru\",\"data\":{\"return_url\":null}}";

        assertThat(signer.signRequestJson(json)).isEqualTo("72d4a48dc7fe890af8beb00cd440c12d");
    }

    /** The mapper must serialize exactly like PHP json_encode (no indent, unescaped unicode, null kept). */
    @Test
    void dengiMapper_serializesLikeDocumentation() throws Exception {
        ObjectMapper m = dengiMapper();
        ObjectNode root = m.createObjectNode();
        root.put("cmd", "getOTP");
        root.put("version", 1005);
        root.put("sid", "1000000001");
        root.put("mktime", "1487602271287");
        root.put("lang", "ru");
        ObjectNode data = m.createObjectNode();
        data.putNull("return_url");
        root.set("data", data);

        assertThat(m.writeValueAsString(root)).isEqualTo(
                "{\"cmd\":\"getOTP\",\"version\":1005,\"sid\":\"1000000001\","
                        + "\"mktime\":\"1487602271287\",\"lang\":\"ru\",\"data\":{\"return_url\":null}}");
    }
}
