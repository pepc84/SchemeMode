package processing.mode.scheme;

public class SchemeError {
    public final String message;
    public final int    line;
    public final String file;

    public SchemeError(String message, int line, String file) {
        this.message = message; this.line = line; this.file = file;
    }
    public SchemeError(String message) { this(message, -1, null); }
    public SchemeError(String message, int line) { this(message, line, null); }

    public static SchemeError fromException(SchemeException e, String file) {
        return new SchemeError(e.getMessage(), e.line, file);
    }

    @Override public String toString() {
        if (line > 0 && file != null) return file + ":" + line + ": " + message;
        if (line > 0) return "line " + line + ": " + message;
        return message;
    }
}

