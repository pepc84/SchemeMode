package processing.mode.scheme;

public class SchemeException extends Exception {
    public final int line;
    public SchemeException(String msg, int line) { super(msg); this.line = line; }
    public SchemeException(String msg) { this(msg, -1); }
}

