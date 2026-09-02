package processing.mode.scheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser — token stream → SExpr tree. No Kawa, no JNA.
 */
public class Parser {

    private final List<Tokenizer.Token> tokens;
    private int pos = 0;

    private Parser(List<Tokenizer.Token> tokens) { this.tokens = tokens; }

    public static List<SExpr> parse(String source) throws SchemeException {
        return new Parser(new Tokenizer(source).tokenizeAll()).parseAll();
    }

    private List<SExpr> parseAll() throws SchemeException {
        List<SExpr> forms = new ArrayList<>();
        while (peek().type != Tokenizer.TT.EOF) {
            SExpr f = parseDatum();
            if (f != null) forms.add(f);
        }
        return forms;
    }

    private SExpr parseDatum() throws SchemeException {
        if (peek().type == Tokenizer.TT.DATUM_COMMENT) {
            consume(); parseDatum(); return parseDatum();
        }
        return parseOne();
    }

    private SExpr parseOne() throws SchemeException {
        Tokenizer.Token t = consume();
        int ln = t.line;
        return switch (t.type) {
            case BOOL      -> new SExpr.Bool(t.value.equals("true"), ln);
            case NUMBER    -> new SExpr.Num(t.value, ln);
            case STRING    -> new SExpr.Str(t.value, ln);
            case CHARACTER -> new SExpr.Char(t.value, ln);
            case SYMBOL    -> new SExpr.Sym(t.value, ln);
            case LPAREN    -> parseList(ln);
            case VECTOR_START    -> parseVector(ln);
            case BYTEVECTOR_START -> parseByteVector(ln);
            case QUOTE -> {
                SExpr inner = parseDatum();
                yield wrap("quote", inner, ln);
            }
            case QUASIQUOTE -> {
                SExpr inner = parseDatum();
                yield wrap("quasiquote", inner, ln);
            }
            case UNQUOTE -> {
                SExpr inner = parseDatum();
                yield wrap("unquote", inner, ln);
            }
            case UNQUOTE_SPLICE -> {
                SExpr inner = parseDatum();
                yield wrap("unquote-splicing", inner, ln);
            }
            case EOF    -> throw new SchemeException("Unexpected EOF", ln);
            default     -> throw new SchemeException("Unexpected token: " + t, ln);
        };
    }

    private SExpr parseList(int ln) throws SchemeException {
        List<SExpr> items = new ArrayList<>();
        SExpr dotTail = null;
        while (peek().type != Tokenizer.TT.RPAREN && peek().type != Tokenizer.TT.EOF) {
            if (peek().type == Tokenizer.TT.DOT) { consume(); dotTail = parseDatum(); break; }
            SExpr item = parseDatum();
            if (item != null) items.add(item);
        }
        expect(Tokenizer.TT.RPAREN, ln);
        SExpr result = dotTail != null ? dotTail : new SExpr.Nil(ln);
        for (int i = items.size() - 1; i >= 0; i--)
            result = new SExpr.Pair(items.get(i), result, i == 0 ? ln : items.get(i).line());
        return result;
    }

    private SExpr parseVector(int ln) throws SchemeException {
        List<SExpr> items = new ArrayList<>();
        while (peek().type != Tokenizer.TT.RPAREN && peek().type != Tokenizer.TT.EOF) {
            SExpr item = parseDatum(); if (item != null) items.add(item);
        }
        expect(Tokenizer.TT.RPAREN, ln);
        return new SExpr.Vec(items, ln);
    }

    private SExpr parseByteVector(int ln) throws SchemeException {
        List<Integer> bytes = new ArrayList<>();
        while (peek().type != Tokenizer.TT.RPAREN && peek().type != Tokenizer.TT.EOF) {
            Tokenizer.Token t = consume();
            if (t.type == Tokenizer.TT.NUMBER) {
                try { bytes.add((int) Long.parseLong(t.value)); } catch (NumberFormatException e) { bytes.add(0); }
            }
        }
        expect(Tokenizer.TT.RPAREN, ln);
        return new SExpr.ByteVec(bytes, ln);
    }

    private SExpr wrap(String name, SExpr inner, int ln) {
        return new SExpr.Pair(new SExpr.Sym(name, ln), new SExpr.Pair(inner, new SExpr.Nil(ln), ln), ln);
    }

    private Tokenizer.Token peek() { return tokens.get(pos); }
    private Tokenizer.Token consume() { return tokens.get(pos++); }
    private void expect(Tokenizer.TT type, int ln) throws SchemeException {
        Tokenizer.Token t = consume();
        if (t.type != type) throw new SchemeException("Expected " + type + " got " + t, ln);
    }
}

