package processing.mode.scheme;

import java.util.ArrayList;
import java.util.List;

/**
 * R7RS lexer. Every token carries a 1-based line number from the .scm file.
 * No Kawa, no JNA — pure Java.
 */
public class Tokenizer {

    public enum TT {
        LPAREN, RPAREN, VECTOR_START, BYTEVECTOR_START,
        QUOTE, QUASIQUOTE, UNQUOTE, UNQUOTE_SPLICE,
        DOT, DATUM_COMMENT, BOOL, NUMBER, CHARACTER, STRING, SYMBOL, EOF
    }

    public static final class Token {
        public final TT     type;
        public final String value;
        public final int    line;
        Token(TT t, String v, int l) { type = t; value = v; line = l; }
        @Override public String toString() { return type + "(" + value + ")@" + line; }
    }

    private final String src;
    private int pos  = 0;
    private int line = 1;

    public Tokenizer(String src) { this.src = src; }

    public List<Token> tokenizeAll() throws SchemeException {
        List<Token> tokens = new ArrayList<>();
        Token t;
        while ((t = next()).type != TT.EOF) tokens.add(t);
        tokens.add(t);
        return tokens;
    }

    private Token next() throws SchemeException {
        skipWS();
        if (pos >= src.length()) return tok(TT.EOF, "");

        int ln = line;
        char c = src.charAt(pos);

        if (c == '#' && peek(1) == '|') { skipBlockComment(); return next(); }
        if (c == '#' && peek(1) == ';') { pos += 2; return tok(TT.DATUM_COMMENT, "#;", ln); }
        if (c == '(')  { pos++; return tok(TT.LPAREN,       "(", ln); }
        if (c == ')')  { pos++; return tok(TT.RPAREN,       ")", ln); }
        if (c == '\'') { pos++; return tok(TT.QUOTE,        "'", ln); }
        if (c == '`')  { pos++; return tok(TT.QUASIQUOTE,   "`", ln); }
        if (c == ',')  {
            pos++;
            if (pos < src.length() && src.charAt(pos) == '@') { pos++; return tok(TT.UNQUOTE_SPLICE, ",@", ln); }
            return tok(TT.UNQUOTE, ",", ln);
        }
        if (c == '"')  return readString(ln);
        if (c == '#')  return readHash(ln);
        if (c == '|')  return readBarSymbol(ln);
        return readAtom(ln);
    }

    private Token readString(int ln) throws SchemeException {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != '"') {
            char c = src.charAt(pos++);
            if (c == '\n') line++;
            if (c == '\\' && pos < src.length()) {
                char e = src.charAt(pos++);
                switch (e) {
                    case 'n' -> sb.append('\n'); case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r'); case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\'); case 'a' -> sb.append('\u0007');
                    case '\n' -> { line++; }
                    case 'x' -> {
                        StringBuilder hex = new StringBuilder();
                        while (pos < src.length() && src.charAt(pos) != ';') hex.append(src.charAt(pos++));
                        if (pos < src.length()) pos++;
                        sb.appendCodePoint(Integer.parseInt(hex.toString(), 16));
                    }
                    default -> { sb.append('\\'); sb.append(e); }
                }
            } else sb.append(c);
        }
        if (pos < src.length()) pos++;
        return tok(TT.STRING, sb.toString(), ln);
    }

    private Token readHash(int ln) throws SchemeException {
        pos++;
        if (pos >= src.length()) return tok(TT.SYMBOL, "#", ln);
        char c = src.charAt(pos);
        if (c == '(')  { pos++; return tok(TT.VECTOR_START, "#(", ln); }
        if (c == 'u' && peek(1) == '8' && peek(2) == '(') { pos += 3; return tok(TT.BYTEVECTOR_START, "#u8(", ln); }
        if (c == 't') { pos++; if (matches("rue")) {} return tok(TT.BOOL, "true", ln); }
        if (c == 'f') { pos++; if (matches("alse")) {} return tok(TT.BOOL, "false", ln); }
        if (c == '\\') { pos++; return tok(TT.CHARACTER, readRaw(), ln); }
        pos--;
        return readAtom(ln);
    }

    private Token readBarSymbol(int ln) throws SchemeException {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != '|') {
            char c = src.charAt(pos++);
            if (c == '\n') line++;
            if (c == '\\' && pos < src.length()) sb.append(src.charAt(pos++));
            else sb.append(c);
        }
        if (pos < src.length()) pos++;
        return tok(TT.SYMBOL, sb.toString(), ln);
    }

    private Token readAtom(int ln) {
        String raw = readRaw();
        if (raw.equals(".")) return tok(TT.DOT, ".", ln);
        try { Long.parseLong(raw); return tok(TT.NUMBER, raw, ln); } catch (NumberFormatException ignored) {}
        try { Double.parseDouble(raw); return tok(TT.NUMBER, raw, ln); } catch (NumberFormatException ignored) {}
        if (raw.equals("+inf.0") || raw.equals("-inf.0") || raw.equals("+nan.0")) return tok(TT.NUMBER, raw, ln);
        return tok(TT.SYMBOL, raw, ln);
    }

    private String readRaw() {
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && !isDelim(src.charAt(pos))) sb.append(src.charAt(pos++));
        return sb.toString();
    }

    private void skipWS() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') { line++; pos++; }
            else if (Character.isWhitespace(c)) pos++;
            else if (c == ';') { while (pos < src.length() && src.charAt(pos) != '\n') pos++; }
            else break;
        }
    }

    private void skipBlockComment() throws SchemeException {
        pos += 2; int depth = 1;
        while (pos + 1 < src.length() && depth > 0) {
            if (src.charAt(pos) == '\n') line++;
            if (src.charAt(pos) == '#' && src.charAt(pos+1) == '|') { depth++; pos += 2; }
            else if (src.charAt(pos) == '|' && src.charAt(pos+1) == '#') { depth--; pos += 2; }
            else pos++;
        }
    }

    private char peek(int off) { int i = pos + off; return i < src.length() ? src.charAt(i) : '\0'; }
    private boolean isDelim(char c) { return Character.isWhitespace(c) || c=='('||c==')'||c=='"'||c==';'||c=='|'; }
    private boolean matches(String s) {
        if (src.startsWith(s, pos)) {
            int after = pos + s.length();
            if (after >= src.length() || isDelim(src.charAt(after))) { pos += s.length(); return true; }
        }
        return false;
    }
    private Token tok(TT t, String v, int l) { return new Token(t, v, l); }
    private Token tok(TT t, String v)        { return new Token(t, v, line); }
}

