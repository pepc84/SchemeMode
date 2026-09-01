package processing.mode.scheme;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LineMapper — maps Python traceback line numbers → original .scm line numbers.
 *
 * The Transpiler emits "# scm:N" comments before every statement.
 * We scan those to build the mapping table.
 */
public class LineMapper {

    private static final Pattern MARKER   = Pattern.compile("^\\s*#\\s*scm:(\\d+)\\s*$");
    private static final Pattern TB_FILE  = Pattern.compile("File \"(.+?)\", line (\\d+)");
    private static final Pattern ERR_LINE = Pattern.compile("^(.+Error|Exception):\\s*(.*)$");

    private final int[] pyToScm;
    private final int   pyLineCount;

    public LineMapper(String generatedPython) {
        String[] lines = generatedPython.split("\n", -1);
        pyLineCount = lines.length;
        pyToScm = new int[pyLineCount + 1];
        int cur = -1;
        for (int i = 0; i < lines.length; i++) {
            Matcher m = MARKER.matcher(lines[i]);
            if (m.matches()) cur = Integer.parseInt(m.group(1));
            pyToScm[i + 1] = cur;
        }
    }

    public int toSchemeLine(int pyLine) {
        if (pyLine < 1 || pyLine > pyLineCount) return -1;
        return pyToScm[pyLine];
    }

    public SchemeError parseTraceback(String stderr, String sketchFile) {
        String[] lines = stderr.split("\n");
        int lastPyLine = -1;
        String errorType = null, errorMsg = null;
        for (String line : lines) {
            Matcher m = TB_FILE.matcher(line);
            if (m.find()) {
                String file = m.group(1);
                // Use any .py frame - the last one in the sketch is what we want
                if (file.endsWith(".py")) {
                    try { lastPyLine = Integer.parseInt(m.group(2)); } catch (NumberFormatException ignored) {}
                }
            }
            Matcher em = ERR_LINE.matcher(line.trim());
            if (em.matches()) { errorType = em.group(1); errorMsg = em.group(2).trim(); }
        }
        int scmLine = lastPyLine >= 0 ? toSchemeLine(lastPyLine) : -1;
        if (errorMsg == null) {
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!lines[i].isBlank()) { errorMsg = lines[i].trim(); break; }
            }
        }
        String msg = errorType != null ? errorType + ": " + errorMsg : (errorMsg != null ? errorMsg : "Python error");
        return new SchemeError(msg, scmLine, sketchFile);
    }
}

