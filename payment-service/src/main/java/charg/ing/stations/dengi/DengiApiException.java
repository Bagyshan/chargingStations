package charg.ing.stations.dengi;

/** Raised when O!Dengi returns an error object or an unprocessable response. */
public class DengiApiException extends RuntimeException {

    private final String cmd;
    private final Integer code;

    public DengiApiException(String cmd, Integer code, String desc) {
        super("O!Dengi " + cmd + " error" + (code != null ? " " + code : "")
                + (desc != null ? ": " + desc : ""));
        this.cmd = cmd;
        this.code = code;
    }

    public String getCmd() { return cmd; }
    public Integer getCode() { return code; }
}
