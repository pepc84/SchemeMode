package processing.mode.scheme;

import java.util.ArrayList;
import java.util.List;

/**
 * SExpr — sealed AST node hierarchy. Every node carries a source line number.
 * Uses sealed interface + records (Java 17+). No Kawa, no JNA.
 */
public sealed interface SExpr permits
    SExpr.Pair, SExpr.Nil, SExpr.Bool, SExpr.Num,
    SExpr.Str, SExpr.Sym, SExpr.Char, SExpr.Vec, SExpr.ByteVec {

    int line();

    record Pair(SExpr car, SExpr cdr, int line) implements SExpr {
        List<SExpr> toList() {
            List<SExpr> list = new ArrayList<>();
            SExpr cur = this;
            while (cur instanceof Pair p) { list.add(p.car); cur = p.cdr; }
            return list;
        }
        String headSym() { return car instanceof Sym s ? s.name() : null; }
    }

    record Nil     (int line) implements SExpr {}
    record Bool    (boolean value, int line) implements SExpr {}
    record Num     (String raw, int line) implements SExpr {}
    record Str     (String value, int line) implements SExpr {}
    record Sym     (String name, int line) implements SExpr {}
    record Vec     (List<SExpr> elements, int line) implements SExpr {}
    record ByteVec (List<Integer> bytes, int line) implements SExpr {}

    record Char(String name, int line) implements SExpr {
        char toJavaChar() {
            return switch (name.toLowerCase()) {
                case "space"     -> ' ';
                case "newline"   -> '\n';
                case "tab"       -> '\t';
                case "return"    -> '\r';
                case "null","nul"-> '\0';
                case "escape","esc" -> '\u001b';
                case "delete","del" -> '\u007f';
                case "alarm"     -> '\u0007';
                case "backspace" -> '\b';
                default -> name.length() == 1 ? name.charAt(0) : '?';
            };
        }
    }

    default boolean isSym(String n) { return this instanceof Sym s && s.name().equals(n); }
    default boolean isForm(String n) { return this instanceof Pair p && p.car().isSym(n); }

    default String defineName() {
        if (!(this instanceof Pair p && p.car().isSym("define"))) return null;
        if (!(p.cdr() instanceof Pair rest)) return null;
        SExpr second = rest.car();
        if (second instanceof Sym s) return s.name();
        if (second instanceof Pair hdr && hdr.car() instanceof Sym s) return s.name();
        return null;
    }

    default boolean isDefineProc() {
        if (!isForm("define")) return false;
        if (!(((Pair) this).cdr() instanceof Pair r)) return false;
        return r.car() instanceof Pair;
    }
}

