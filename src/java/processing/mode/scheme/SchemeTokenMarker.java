package processing.mode.scheme;

import processing.app.syntax.*;
import javax.swing.text.Segment;
import java.nio.file.*;

public class SchemeTokenMarker extends PdeTokenMarker {

    public SchemeTokenMarker() {
        keywordColoring = new KeywordMap(false);
    }

    @Override
    public void addColoring(String keyword, String coloring) {
        try { Files.write(Paths.get("/tmp/scm.txt"), ("add:" + keyword + "\n").getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception _e) {}
        super.addColoring(keyword, coloring);
    }

    @Override
    public byte markTokensImpl(byte token, Segment line, int lineIndex) {
        try { Files.write(Paths.get("/tmp/scm.txt"), ("mark:" + lineIndex + "\n").getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception _e) {}
        if (keywordColoring == null) keywordColoring = new KeywordMap(false);

        char[] array = line.array;
        int offset = line.offset;
        int length = line.count;
        int end = offset + length;
        int pos = offset;

        while (pos < end) {
            char c = array[pos];
            if (c == ';') {
                addToken(end - pos, Token.COMMENT1);
                return Token.NULL;
            }
            if (c == '"') {
                int start = pos; pos++;
                while (pos < end) {
                    if (array[pos] == '\\') { pos += 2; continue; }
                    if (array[pos] == '"')  { pos++; break; }
                    pos++;
                }
                addToken(pos - start, Token.LITERAL1);
                continue;
            }
            if (!isDelim(c)) {
                int start = pos;
                while (pos < end && !isDelim(array[pos])) pos++;
                int len = pos - start;
                byte id = keywordColoring.lookup(line, start, len, true);
                if (id == Token.NULL) id = keywordColoring.lookup(line, start, len, false);
                try { Files.write(Paths.get("/tmp/scm.txt"), (("'" + new String(array, start, len) + "'=" + id + "\n")).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception _e) {}
                addToken(len, id != Token.NULL ? id : Token.NULL);
                continue;
            }
            addToken(1, Token.NULL);
            pos++;
        }
        return Token.NULL;
    }

    private static boolean isDelim(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' ||
               c == ' ' || c == '\t' || c == '\'' || c == '`' || c == ',';
    }
}
