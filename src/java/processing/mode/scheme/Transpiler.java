package processing.mode.scheme;

import java.util.*;

/**
 * Transpiler — Scheme AST → Python mewnala script.
 *
 * No Kawa. No JNA. Pure Java 17.
 * Pipeline: .scm source → Parser → SExpr tree → this class → .py file → python3 + mewnala
 *
 * mewnala facts (0.0.8):
 *   - Colors 0-255 integers
 *   - run() discovers setup/draw by name in caller globals
 *   - mouse_x, width, etc. are dynamic module attributes (not functions)
 *   - Full API in mewnala/__init__.pyi
 */
public class Transpiler {

    // mewnala functions: scheme-name → python_name (simple snake_case mapping)
    private static final Set<String> MEWNALA_FNS = Set.of(
        "ellipse","circle","rect","square","line","point","triangle","quad","arc",
        "bezier","curve","begin-shape","end-shape","begin-contour","end-contour",
        "vertex","bezier-vertex","curve-vertex","quadratic-vertex",
        "fill","no-fill","stroke","no-stroke","stroke-weight","stroke-cap","stroke-join",
        "tint","no-tint","background",
        "push-matrix","pop-matrix","reset-matrix","translate","rotate",
        "rotate-x","rotate-y","rotate-z","scale","shear-x","shear-y",
        "color-mode","color-hex","hsla","hsva","hwba","lab","lch","oklab","oklch",
        "srgb","linear-rgb","xyz",
        "text","text-size","text-align","text-leading","text-style","text-weight",
        "text-font","text-width","text-ascent","text-descent","text-bounds","text-wrap",
        "text-feature","no-text-feature","text-variation","text-to-paths","text-to-contours",
        "text-to-model","text-to-points","text-glyph-colors","text-glyph-rects",
        "text-lines","text-line-count",
        "image","image-mode","load-image","create-image",
        "size","full-screen","window-title","window-resize","window-move",
        "window-resizable","window-decorated","window-opacity","window-visible",
        "window-maximize","window-iconify","window-restore","window-always-on-top",
        "window-center-on","window-position-on",
        "mode-2d","mode-3d",
        "sphere","box","cylinder","cone","torus",
        "plane","capsule","draw-tetrahedron","draw-conical-frustum","draw-geometry",
        "camera-position","camera-look-at","camera-center","camera-distance","camera-speed",
        "camera-min-distance","camera-max-distance","camera-reset",
        "orbit-camera","pan-camera","free-camera","disable-camera",
        "directional-light","point-light","spot-light",
        "emissive","metallic","roughness","unlit","use-material","blend-mode",
        "ellipse-mode","rect-mode","no-loop","redraw","flush",
        "key-is-down","key-just-pressed","pixel-density","monitors","primary-monitor",
        "display-density","load-gltf","particles","kernel-noise","kernel-transform",
        "midi-connect","midi-disconnect","midi-list-ports","midi-note-on","midi-note-off",
        "midi-play-notes","midi-refresh-ports","create-color","vec2","vec3","vec4","quat"
    );

    // Dynamic attributes: accessed as plain variables in Python (no call)
    private static final Map<String,String> DYNATTR = new LinkedHashMap<>();
    static {
        DYNATTR.put("mouse-x","mouse_x"); DYNATTR.put("mouse-y","mouse_y");
        DYNATTR.put("pmouse-x","pmouse_x"); DYNATTR.put("pmouse-y","pmouse_y");
        DYNATTR.put("mouse-is-pressed","mouse_is_pressed");
        DYNATTR.put("mouse-button","mouse_button"); DYNATTR.put("mouse-wheel","mouse_wheel");
        DYNATTR.put("moved-x","moved_x"); DYNATTR.put("moved-y","moved_y");
        DYNATTR.put("key","key"); DYNATTR.put("key-code","key_code");
        DYNATTR.put("key-is-pressed","key_is_pressed");
        DYNATTR.put("width","width"); DYNATTR.put("height","height");
        DYNATTR.put("focused","focused"); DYNATTR.put("pixel-width","pixel_width");
        DYNATTR.put("pixel-height","pixel_height"); DYNATTR.put("frame-count","frame_count");
        DYNATTR.put("delta-time","delta_time"); DYNATTR.put("elapsed-time","elapsed_time");
        DYNATTR.put("display-width","display_width"); DYNATTR.put("display-height","display_height");
        DYNATTR.put("window-x","window_x"); DYNATTR.put("window-y","window_y");
    }

    // Constants
    private static final Map<String,String> CONST = new LinkedHashMap<>();
    static {
        CONST.put("PI","PI"); CONST.put("TWO-PI","TWO_PI"); CONST.put("HALF-PI","HALF_PI");
        CONST.put("QUARTER-PI","QUARTER_PI"); CONST.put("TAU","TAU");
        CONST.put("DEG-TO-RAD","DEG_TO_RAD"); CONST.put("RAD-TO-DEG","RAD_TO_DEG");
        CONST.put("CENTER","CENTER"); CONST.put("CORNER","CORNER"); CONST.put("CORNERS","CORNERS");
        CONST.put("RADIUS","RADIUS"); CONST.put("LEFT","LEFT"); CONST.put("RIGHT","RIGHT");
        CONST.put("OPEN","OPEN"); CONST.put("CHORD","CHORD"); CONST.put("PIE","PIE");
        CONST.put("CLOSE","CLOSE");
        CONST.put("POINTS","POINTS"); CONST.put("LINES","LINES");
        CONST.put("TRIANGLES","TRIANGLES"); CONST.put("TRIANGLE-FAN","TRIANGLE_FAN");
        CONST.put("TRIANGLE-STRIP","TRIANGLE_STRIP"); CONST.put("QUADS","QUADS");
        CONST.put("QUAD-STRIP","QUAD_STRIP"); CONST.put("POLYGON","POLYGON");
        CONST.put("ROUND","ROUND"); CONST.put("SQUARE","SQUARE"); CONST.put("PROJECT","PROJECT");
        CONST.put("MITER","MITER"); CONST.put("BEVEL","BEVEL");
        CONST.put("HSL","HSL"); CONST.put("HSV","HSV"); CONST.put("HWB","HWB");
        CONST.put("LAB","LAB"); CONST.put("LCH","LCH"); CONST.put("OKLAB","OKLAB");
        CONST.put("OKLCH","OKLCH"); CONST.put("SRGB","SRGB"); CONST.put("XYZ","XYZ");
        CONST.put("LINEAR","LINEAR");
        CONST.put("ENTER","ENTER"); CONST.put("BACKSPACE","BACKSPACE"); CONST.put("TAB","TAB");
        CONST.put("DELETE","DELETE"); CONST.put("ESCAPE","ESCAPE"); CONST.put("HOME","HOME");
        CONST.put("END","END"); CONST.put("INSERT","INSERT"); CONST.put("UP","UP");
        CONST.put("DOWN","DOWN"); CONST.put("LEFT-ARROW","LEFT_ARROW");
        CONST.put("RIGHT-ARROW","RIGHT_ARROW"); CONST.put("PAGE-UP","PAGE_UP");
        CONST.put("PAGE-DOWN","PAGE_DOWN"); CONST.put("SHIFT","SHIFT");
        CONST.put("CONTROL","CONTROL"); CONST.put("ALT","ALT"); CONST.put("SUPER","SUPER");
        for (int i = 1; i <= 12; i++) CONST.put("F"+i, "F"+i);
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final StringBuilder out = new StringBuilder();
    private int lastScmLine = -1;
    private boolean inFunction = false;
    private final Set<String> globalVars = new LinkedHashSet<>();
    private final Set<String> localVars  = new LinkedHashSet<>();

    // ── Entry point ───────────────────────────────────────────────────────────

    public String transpile(List<SExpr> forms, String sketchName) throws SchemeException {
        // collect top-level define names
        for (SExpr f : forms)
            if (f.isForm("define")) { String n = f.defineName(); if (n != null) globalVars.add(snake(n)); }

        emit("# Generated by SchemeMode — " + sketchName + "\n");
        emit("from mewnala import *\n");
        emit("from mewnala.math import *\n");
        emit("import math as _math\n\n");
        emit("def _err(*a): raise Exception(' '.join(str(x) for x in a))\n\n");
        emit("import random as _random\n");
        emit("def random(*args):\n");
        emit("    if len(args)==1: return _random.uniform(0, args[0])\n");
        emit("    return _random.uniform(args[0], args[1])\n\n");
        emit("def random_gaussian(): return _random.gauss(0, 1)\n\n");
        emit("def noise(*args): return _random.random()\n\n");

        for (SExpr f : forms) {
            if (f.isForm("import")) continue;
            emitTopLevel(f);
            emit("\n");
        }

        if (!out.toString().contains("\nrun()")) emit("\nrun()\n");
        return out.toString();
    }

    // ── Top-level ─────────────────────────────────────────────────────────────

    private void emitTopLevel(SExpr f) throws SchemeException {
        if (f.isForm("define")) { emitDefine(f, 0); return; }
        if (f.isForm("define-record-type")) { emitRecordType(f); return; }
        if (f.isForm("define-syntax") || f.isForm("let-syntax")) {
            marker(f.line(), 0); emit("# define-syntax (skipped)\n"); return;
        }
        marker(f.line(), 0);
        emitStmt(f, 0);
    }

    // ── define ────────────────────────────────────────────────────────────────

    private void emitDefine(SExpr f, int ind) throws SchemeException {
        List<SExpr> parts = list(f).subList(1, list(f).size());
        if (parts.isEmpty()) throw new SchemeException("empty define", f.line());
        SExpr head = parts.get(0);
        if (head instanceof SExpr.Pair hdr) {
            emitProcDef(hdr, parts.subList(1, parts.size()), ind, f.line());
        } else if (head instanceof SExpr.Sym s) {
            String py = snake(s.name());
            if (ind == 0) globalVars.add(py);
            marker(f.line(), ind); indent(ind);
            emit(py + " = ");
            if (parts.size() > 1) emitExpr(parts.get(1), ind); else emit("None");
            emit("\n");
        }
    }

    private static final java.util.Set<String> LIFECYCLE_FNS = java.util.Set.of(
        "setup", "draw", "mouse_pressed", "mouse_released",
        "mouse_moved", "mouse_dragged", "mouse_wheel_event",
        "key_pressed", "key_released"
    );

    

    private void emitProcDef(SExpr.Pair hdr, List<SExpr> body, int ind, int srcLine)
            throws SchemeException {
        List<SExpr> hl = hdr.toList();
        String name = snake(((SExpr.Sym) hl.get(0)).name());
        marker(srcLine, ind); indent(ind);
        emit("def " + name + "(");

        List<String> params = new ArrayList<>();
        boolean rest = false; String restParam = null;
        SExpr cur = hdr.cdr();
        while (cur instanceof SExpr.Pair p) {
            if (p.car() instanceof SExpr.Sym s) params.add(snake(s.name()));
            cur = p.cdr();
        }
        if (cur instanceof SExpr.Sym s) { rest = true; restParam = snake(s.name()); }

        emit(String.join(", ", params));
        if (rest) { if (!params.isEmpty()) emit(", "); emit("*" + restParam); }
        emit("):\n");

        boolean wasIn = inFunction;
        Set<String> wasLocal = new LinkedHashSet<>(localVars);
        inFunction = true; localVars.clear(); localVars.addAll(params);
        if (rest) localVars.add(restParam);

        // Hoist global declarations to top of function
        Set<String> setTargets = findSetTargets(body);
        for (String g : setTargets) {
            if (globalVars.contains(g) && !localVars.contains(g)) {
                indent(ind + 1); emit("global " + g + "\n");
                localVars.add(g);
            }
        }

        emitBody(body, ind + 1, LIFECYCLE_FNS.contains(name));

        inFunction = wasIn; localVars.clear(); localVars.addAll(wasLocal);
    }

    private void emitBody(List<SExpr> body, int ind) throws SchemeException {
        emitBody(body, ind, false);
    }

    private void emitBody(List<SExpr> body, int ind, boolean noReturn) throws SchemeException {
        emitBody(body, ind, false);
    }

    

    // ── define-record-type ────────────────────────────────────────────────────

    private void emitRecordType(SExpr f) throws SchemeException {
        List<SExpr> p = list(f);
        String cls = pascal(sym(p.get(1)));
        List<SExpr> ctor = list(p.get(2));
        String ctorName = snake(sym(ctor.get(0)));
        List<String> fields = new ArrayList<>();
        for (int i = 1; i < ctor.size(); i++) fields.add(snake(sym(ctor.get(i))));
        String pred = snake(sym(p.get(3)));

        marker(f.line(), 0);
        emit("class " + cls + ":\n");
        emit("    __slots__ = " + pyStrList(fields) + "\n");
        emit("    def __init__(self, " + String.join(", ", fields) + "):\n");
        for (String fld : fields) emit("        self." + fld + " = " + fld + "\n");
        emit(ctorName + " = " + cls + "\n");
        emit(pred + " = lambda x: isinstance(x, " + cls + ")\n");

        for (int i = 4; i < p.size(); i++) {
            List<SExpr> fd = list(p.get(i));
            String fld = snake(sym(fd.get(0)));
            String acc = snake(sym(fd.get(1)));
            emit(acc + " = lambda r: r." + fld + "\n");
            if (fd.size() > 2) {
                String mod = snake(sym(fd.get(2)));
                emit(mod + " = lambda r, v: setattr(r, '" + fld + "', v)\n");
            }
        }
        emit("\n");
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private void emitStmt(SExpr e, int ind) throws SchemeException {
        if (e instanceof SExpr.Nil) { indent(ind); emit("pass\n"); return; }
        if (e.isForm("define"))           { emitDefine(e, ind); return; }
        if (e.isForm("define-record-type")){ emitRecordType(e); return; }
        if (e.isForm("set!"))             { emitSet(e, ind); return; }
        if (e.isForm("begin"))            { emitBegin(e, ind); return; }
        if (e.isForm("if"))               { emitIf(e, ind, true); return; }
        if (e.isForm("cond"))             { emitCond(e, ind); return; }
        if (e.isForm("case"))             { emitCase(e, ind); return; }
        if (e.isForm("when"))             { emitWhen(e, ind, false); return; }
        if (e.isForm("unless"))           { emitWhen(e, ind, true); return; }
        if (e.isForm("let") || e.isForm("let*") || e.isForm("letrec") || e.isForm("letrec*"))
                                          { emitLet(e, ind); return; }
        if (e.isForm("do"))               { emitDo(e, ind); return; }
        if (e.isForm("for-each"))         { emitForEach(e, ind); return; }
        if (e.isForm("with-matrix"))      { emitWithMatrix(e, ind); return; }
        if (e.isForm("display"))          { indent(ind); emit("print("); emitExpr(nth(e,1), ind); emit(", end='')\n"); return; }
        if (e.isForm("newline"))          { indent(ind); emit("print()\n"); return; }
        if (e.isForm("write"))            { indent(ind); emit("print(repr("); emitExpr(nth(e,1),ind); emit("), end='')\n"); return; }
        if (e.isForm("define-syntax") || e.isForm("let-syntax")) { indent(ind); emit("pass  # syntax\n"); return; }
        indent(ind); emitExpr(e, ind); emit("\n");
    }

    private void emitSet(SExpr e, int ind) throws SchemeException {
        String name = snake(sym(nth(e,1)));
        indent(ind);

        emit(name + " = "); emitExpr(nth(e,2), ind); emit("\n");
    }

    private void emitBegin(SExpr e, int ind) throws SchemeException {
        List<SExpr> stmts = list(e).subList(1, list(e).size());
        for (SExpr s : stmts) { marker(s.line(), ind); emitStmt(s, ind); }
    }

    private void emitIf(SExpr e, int ind, boolean asStmt) throws SchemeException {
        SExpr test = nth(e,1), then = nth(e,2);
        SExpr els = list(e).size() > 3 ? nth(e,3) : null;
        if (asStmt) {
            indent(ind); emit("if "); emitExpr(test, ind); emit(":\n");
            marker(then.line(), ind+1); emitStmt(then, ind+1);
            if (els != null) { indent(ind); emit("else:\n"); marker(els.line(), ind+1); emitStmt(els, ind+1); }
        } else {
            emit("("); emitExpr(then, ind); emit(" if ("); emitExpr(test, ind); emit(") else ");
            if (els != null) emitExpr(els, ind); else emit("None");
            emit(")");
        }
    }

    private void emitCond(SExpr e, int ind) throws SchemeException {
        List<SExpr> clauses = list(e).subList(1, list(e).size());
        boolean first = true;
        for (SExpr clause : clauses) {
            List<SExpr> c = list(clause);
            boolean isElse = c.get(0).isSym("else");
            indent(ind);
            if (isElse) emit("else:\n");
            else { emit(first ? "if " : "elif "); emitExpr(c.get(0), ind); emit(":\n"); }
            List<SExpr> body = isElse ? c.subList(1,c.size()) : c.subList(1,c.size());
            if (body.isEmpty()) { indent(ind+1); emit("pass\n"); }
            else for (SExpr b : body) emitStmt(b, ind+1);
            first = false;
        }
    }

    private void emitCase(SExpr e, int ind) throws SchemeException {
        indent(ind); emit("_cv = "); emitExpr(nth(e,1), ind); emit("\n");
        List<SExpr> clauses = list(e).subList(2, list(e).size());
        boolean first = true;
        for (SExpr clause : clauses) {
            List<SExpr> c = list(clause);
            boolean isElse = c.get(0).isSym("else");
            indent(ind);
            if (isElse) emit("else:\n");
            else { emit(first ? "if " : "elif "); emit("_cv in "); emitQuote(c.get(0)); emit(":\n"); }
            for (int i = 1; i < c.size(); i++) emitStmt(c.get(i), ind+1);
            first = false;
        }
    }

    private void emitWhen(SExpr e, int ind, boolean negate) throws SchemeException {
        indent(ind); emit("if "); if (negate) emit("not (");
        emitExpr(nth(e,1), ind); if (negate) emit(")"); emit(":\n");
        List<SExpr> body = list(e).subList(2, list(e).size());
        if (body.isEmpty()) { indent(ind+1); emit("pass\n"); }
        else for (SExpr b : body) emitStmt(b, ind+1);
    }

    private void emitLet(SExpr e, int ind) throws SchemeException {
        List<SExpr> parts = list(e);
        SExpr second = parts.get(1);
        // Named let
        if (second instanceof SExpr.Sym loopSym) {
            String loopName = snake(loopSym.name());
            List<SExpr> bindings = list(parts.get(2));
            List<SExpr> body = parts.subList(3, parts.size());
            List<String> params = new ArrayList<>();
            List<SExpr> inits  = new ArrayList<>();
            for (SExpr b : bindings) {
                List<SExpr> bl = list(b);
                params.add(snake(sym(bl.get(0))));
                inits.add(bl.size() > 1 ? bl.get(1) : new SExpr.Nil(b.line()));
            }
            marker(e.line(), ind); indent(ind);
            emit("def " + loopName + "(" + String.join(", ", params) + "):\n");
            boolean wi = inFunction; Set<String> wl = new LinkedHashSet<>(localVars);
            inFunction = true; localVars.clear(); localVars.addAll(params);
            emitBody(body, ind+1);
            inFunction = wi; localVars.clear(); localVars.addAll(wl);
            indent(ind); emit(loopName + "("); emitArgs(inits, ind); emit(")\n");
            return;
        }
        // Regular let
        List<SExpr> bindings = list(second);
        List<SExpr> body = parts.subList(2, parts.size());
        for (SExpr b : bindings) {
            List<SExpr> bl = list(b);
            String varName = snake(sym(bl.get(0)));
            localVars.add(varName);
            marker(b.line(), ind); indent(ind);
            emit(varName + " = ");
            if (bl.size() > 1) emitExpr(bl.get(1), ind); else emit("None");
            emit("\n");
        }
        emitBody(body, ind);
    }

    private void emitDo(SExpr e, int ind) throws SchemeException {
        List<SExpr> parts = list(e);
        List<SExpr> varSpecs = list(parts.get(1));
        List<SExpr> term = list(parts.get(2));
        List<SExpr> body = parts.subList(3, parts.size());
        for (SExpr vs : varSpecs) {
            List<SExpr> v = list(vs);
            indent(ind); emit(snake(sym(v.get(0))) + " = "); emitExpr(v.get(1), ind); emit("\n");
        }
        indent(ind); emit("while True:\n");
        indent(ind+1); emit("if "); emitExpr(term.get(0), ind+1); emit(":\n");
        if (term.size() > 1) { for (int i = 1; i < term.size(); i++) emitStmt(term.get(i), ind+2); }
        else { indent(ind+2); emit("break\n"); }
        for (SExpr b : body) emitStmt(b, ind+1);
        // steps
        List<String> stepVars = new ArrayList<>();
        List<SExpr> stepExprs = new ArrayList<>();
        for (SExpr vs : varSpecs) {
            List<SExpr> v = list(vs);
            if (v.size() > 2) { stepVars.add(snake(sym(v.get(0)))); stepExprs.add(v.get(2)); }
        }
        if (!stepVars.isEmpty()) {
            indent(ind+1); emit("_ds = ("); emitArgs(stepExprs, ind+1); emit(")\n");
            for (int i = 0; i < stepVars.size(); i++) {
                indent(ind+1); emit(stepVars.get(i) + " = _ds[" + i + "]\n");
            }
        }
    }

    private void emitForEach(SExpr e, int ind) throws SchemeException {
        List<SExpr> args = list(e).subList(1, list(e).size());
        indent(ind);
        if (args.size() == 2) {
            emit("for _fi in "); emitExpr(args.get(1), ind); emit(":\n");
            indent(ind+1); emitExpr(args.get(0), ind+1); emit("(_fi)\n");
        } else {
            emit("for _fi in zip("); emitArgs(args.subList(1, args.size()), ind); emit("):\n");
            indent(ind+1); emitExpr(args.get(0), ind+1); emit("(*_fi)\n");
        }
    }

    private void emitWithMatrix(SExpr e, int ind) throws SchemeException {
        indent(ind); emit("push_matrix()\n");
        for (SExpr b : list(e).subList(1, list(e).size())) emitStmt(b, ind);
        indent(ind); emit("pop_matrix()\n");
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    private void emitExpr(SExpr e, int ind) throws SchemeException {
        if (e instanceof SExpr.Bool b)   { emit(b.value() ? "True" : "False"); }
        else if (e instanceof SExpr.Num n)    { emit(n.raw()); }
        else if (e instanceof SExpr.Str s)    { emitStr(s.value()); }
        else if (e instanceof SExpr.Char ch)  { emit("'" + esc(ch.toJavaChar()) + "'"); }
        else if (e instanceof SExpr.Nil)      { emit("[]"); }
        else if (e instanceof SExpr.Vec v)    {
            emit("["); for (int i = 0; i < v.elements().size(); i++) { if (i>0) emit(", "); emitExpr(v.elements().get(i), ind); } emit("]");
        }
        else if (e instanceof SExpr.ByteVec bv) {
            emit("bytearray(["); for (int i = 0; i < bv.bytes().size(); i++) { if (i>0) emit(", "); emit(String.valueOf(bv.bytes().get(i))); } emit("])");
        }
        else if (e instanceof SExpr.Sym sym)  { emitSym(sym); }
        else if (e instanceof SExpr.Pair pair){ emitApp(pair, ind); }
    }

    private void emitSym(SExpr.Sym sym) {
        String n = sym.name();
        if (CONST.containsKey(n.toUpperCase())) { emit(CONST.get(n.toUpperCase())); return; }
        if (CONST.containsKey(n)) { emit(CONST.get(n)); return; }
        if (DYNATTR.containsKey(n)) { emit(DYNATTR.get(n)); return; }
        emit(snake(n));
    }

    private void emitApp(SExpr.Pair pair, int ind) throws SchemeException {
        SExpr head = pair.car();
        List<SExpr> args = pair.cdr() instanceof SExpr.Nil ? List.of() : list(pair).subList(1, list(pair).size());

        if (!(head instanceof SExpr.Sym sym)) {
            emit("("); emitExpr(head, ind); emit(")("); emitArgs(args, ind); emit(")"); return;
        }

        String name = sym.name();
        switch (name) {
            case "quote"      -> { emitQuote(args.isEmpty() ? new SExpr.Nil(pair.line()) : args.get(0)); return; }
            case "quasiquote" -> { emitQQ(args.get(0), ind); return; }
            case "lambda"     -> { emitLambda(pair, ind); return; }
            case "if"         -> { emitIf(pair, ind, false); return; }
            case "and"        -> { emitAndOr(args, ind, "and", "True"); return; }
            case "or"         -> { emitAndOr(args, ind, "or",  "False"); return; }
            case "not"        -> { emit("(not ("); emitExpr(args.get(0), ind); emit("))"); return; }
            case "begin"      -> { emitBeginExpr(pair, ind); return; }
            case "let","let*","letrec","letrec*" -> { emitLetExpr(pair, ind); return; }
            case "values"     -> { if (args.size()==1) emitExpr(args.get(0),ind); else { emit("("); emitArgs(args,ind); emit(")"); } return; }
            case "set!"       -> { emitSetExpr(pair, ind); return; }
            case "when","unless" -> { emit("None"); return; }
            case "vector"     -> { emit("["); emitArgs(args, ind); emit("]"); return; }
            case "vector-ref" -> { emitExpr(args.get(0),ind); emit("["); emitExpr(args.get(1),ind); emit("]"); return; }
            case "vector-set!" -> { emitExpr(args.get(0),ind); emit("["); emitExpr(args.get(1),ind); emit("] = "); emitExpr(args.get(2),ind); return; }
            case "vector-length","string-length","length" -> { emit("len("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "make-vector" -> { emit("[None]*"); emitExpr(args.get(0),ind); return; }
            case "apply"      -> { emitApply(args, ind); return; }
            case "map"        -> { emit("list(map("); emitExpr(args.get(0),ind); for (int i=1;i<args.size();i++){emit(", ");emitExpr(args.get(i),ind);} emit("))"); return; }
            case "filter"     -> { emit("list(filter("); emitExpr(args.get(0),ind); emit(", "); emitExpr(args.get(1),ind); emit("))"); return; }
            case "for-each"   -> { emit("["); emitExpr(args.get(0),ind); emit("(_i) for _i in "); emitExpr(args.get(1),ind); emit("]"); return; }
            case "fold-left","foldl" -> { emit("__import__('functools').reduce("); emitExpr(args.get(0),ind); emit(", "); emitExpr(args.get(2),ind); emit(", "); emitExpr(args.get(1),ind); emit(")"); return; }
            case "error"      -> { emit("_err("); emitArgs(args,ind); emit(")"); return; }
            case "void"       -> { emit("None"); return; }
            case "display"    -> { emit("print("); emitExpr(args.get(0),ind); emit(", end='')"); return; }
            case "newline"    -> { emit("print()"); return; }
            case "number->string","symbol->string" -> { emit("str("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "string->number" -> { emit("float("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "exact->inexact","inexact" -> { emit("float("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "inexact->exact","exact","floor->exact","truncate->exact" -> { emit("int("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "char->integer" -> { emit("ord("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "integer->char" -> { emit("chr("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "string->list"  -> { emit("list("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "list->string"  -> { emit("''.join("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "string-append" -> { emitBinOp(args, " + ", ind); return; }
            case "substring"  -> { emitExpr(args.get(0),ind); emit("["); emitExpr(args.get(1),ind); emit(":"); if(args.size()>2) emitExpr(args.get(2),ind); emit("]"); return; }
            case "string-ref" -> { emitExpr(args.get(0),ind); emit("["); emitExpr(args.get(1),ind); emit("]"); return; }
            case "string-upcase"  -> { emitExpr(args.get(0),ind); emit(".upper()"); return; }
            case "string-downcase" -> { emitExpr(args.get(0),ind); emit(".lower()"); return; }
            case "string=?"  -> { emitExpr(args.get(0),ind); emit(" == "); emitExpr(args.get(1),ind); return; }
            case "string<?"  -> { emitExpr(args.get(0),ind); emit(" < ");  emitExpr(args.get(1),ind); return; }
            case "string>?"  -> { emitExpr(args.get(0),ind); emit(" > ");  emitExpr(args.get(1),ind); return; }
            case "iota"       -> {
                if (args.size()==1) { emit("list(range("); emitExpr(args.get(0),ind); emit("))"); }
                else { emit("list(range("); emitExpr(args.get(1),ind); emit(", "); emitExpr(args.get(1),ind); emit(" + "); emitExpr(args.get(0),ind); emit("))"); }
                return;
            }
        }

        // arithmetic
        String arith = arithOp(name, args, ind);
        if (arith != null) { emit(arith); return; }

        // comparison / predicate
        String cmp = cmpOp(name, args, ind);
        if (cmp != null) { emit(cmp); return; }

        // list ops
        String lop = listOp(name, args, ind);
        if (lop != null) { emit(lop); return; }

        // mewnala functions
        if (MEWNALA_FNS.contains(name)) {
            emit(snake(name) + "("); emitArgs(args, ind); emit(")"); return;
        }

        // general call
        emit(snake(name) + "("); emitArgs(args, ind); emit(")");
    }

    private void emitLambda(SExpr.Pair form, int ind) throws SchemeException {
        List<SExpr> parts = list(form);
        SExpr paramForm = parts.get(1);
        List<SExpr> body = parts.subList(2, parts.size());
        List<String> params = new ArrayList<>();
        boolean rest = false; String restP = null;
        if (paramForm instanceof SExpr.Pair pp) {
            SExpr cur = pp;
            while (cur instanceof SExpr.Pair p) { if (p.car() instanceof SExpr.Sym s) params.add(snake(s.name())); cur = p.cdr(); }
            if (cur instanceof SExpr.Sym s) { rest = true; restP = snake(s.name()); }
        } else if (paramForm instanceof SExpr.Sym s) { rest = true; restP = snake(s.name()); }
        String ps = String.join(", ", params) + (rest ? (params.isEmpty()?"":", ") + "*" + restP : "");
        if (body.size() == 1 && isSimple(body.get(0))) {
            emit("(lambda " + ps + ": "); emitExpr(body.get(0), ind); emit(")");
        } else {
            // multi-body: emit as immediately invoked nested def
            // For now emit lambda returning last expr (limitation)
            emit("(lambda " + ps + ": ("); emitExpr(body.get(body.size()-1), ind); emit("))");
        }
    }

    private void emitBeginExpr(SExpr.Pair form, int ind) throws SchemeException {
        List<SExpr> items = list(form).subList(1, list(form).size());
        if (items.isEmpty()) { emit("None"); return; }
        if (items.size() == 1) { emitExpr(items.get(0), ind); return; }
        emit("(");
        for (int i = 0; i < items.size(); i++) { if (i>0) emit(" or "); emitExpr(items.get(i), ind); }
        emit(")");
    }

    private void emitLetExpr(SExpr.Pair form, int ind) throws SchemeException {
        List<SExpr> parts = list(form);
        if (parts.get(1) instanceof SExpr.Sym) { emit("None"); return; } // named let in expr pos
        List<SExpr> bindings = list(parts.get(1));
        List<SExpr> body = parts.subList(2, parts.size());
        List<String> ps = new ArrayList<>();
        List<String> is = new ArrayList<>();
        for (SExpr b : bindings) {
            List<SExpr> bl = list(b);
            ps.add(snake(sym(bl.get(0))));
            StringBuilder sb = new StringBuilder();
            Transpiler sub = subTx(); sub.emitExpr(bl.size()>1 ? bl.get(1) : new SExpr.Nil(0), ind);
            is.add(sub.out.toString());
        }
        emit("(lambda " + String.join(", ", ps) + ": ");
        emitExpr(body.get(body.size()-1), ind);
        emit(")(" + String.join(", ", is) + ")");
    }

    private void emitSetExpr(SExpr.Pair form, int ind) throws SchemeException {
        // set! in expression position — just return the value
        emit("("); emitExpr(nth(form, 2), ind); emit(")");
    }

    private void emitApply(List<SExpr> args, int ind) throws SchemeException {
        emitExpr(args.get(0), ind); emit("(*(");
        if (args.size() == 2) emitExpr(args.get(1), ind);
        else { emit("["); emitArgs(args.subList(1, args.size()-1), ind); emit("] + list("); emitExpr(args.get(args.size()-1), ind); emit(")"); }
        emit("))");
    }

    private void emitAndOr(List<SExpr> args, int ind, String op, String empty) throws SchemeException {
        if (args.isEmpty()) { emit(empty); return; }
        emit("(");
        for (int i = 0; i < args.size(); i++) { if (i>0) emit(" " + op + " "); emitExpr(args.get(i), ind); }
        emit(")");
    }

    private void emitBinOp(List<SExpr> args, String op, int ind) throws SchemeException {
        emit("("); for (int i=0;i<args.size();i++){if(i>0)emit(op);emitExpr(args.get(i),ind);} emit(")");
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    private String arithOp(String n, List<SExpr> args, int ind) throws SchemeException {
        if (args.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        switch (n) {
            case "+" -> { if(args.size()==1){sb.append("(+");apE(sb,args.get(0),ind);sb.append(")");} else { sb.append("("); apE(sb,args.get(0),ind); for(int i=1;i<args.size();i++){sb.append(" + ");apE(sb,args.get(i),ind);} sb.append(")"); } return sb.toString(); }
            case "-" -> { if(args.size()==1){sb.append("(-");apE(sb,args.get(0),ind);sb.append(")");} else {sb.append("(");apE(sb,args.get(0),ind);for(int i=1;i<args.size();i++){sb.append(" - ");apE(sb,args.get(i),ind);}sb.append(")");} return sb.toString(); }
            case "*" -> { sb.append("("); apE(sb,args.get(0),ind); for(int i=1;i<args.size();i++){sb.append(" * ");apE(sb,args.get(i),ind);} sb.append(")"); return sb.toString(); }
            case "/" -> { if(args.size()==1){sb.append("(1/");apE(sb,args.get(0),ind);sb.append(")");} else {sb.append("(");apE(sb,args.get(0),ind);for(int i=1;i<args.size();i++){sb.append(" / ");apE(sb,args.get(i),ind);}sb.append(")");} return sb.toString(); }
            case "quotient","floor-quotient" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" // "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "remainder","modulo","floor-remainder" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" % "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "expt"   -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" ** "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "abs"    -> { sb.append("abs("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "max"    -> { sb.append("max("); apAll(sb,args,ind); sb.append(")"); return sb.toString(); }
            case "min"    -> { sb.append("min("); apAll(sb,args,ind); sb.append(")"); return sb.toString(); }
            case "floor"  -> { sb.append("_math.floor("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "ceiling" -> { sb.append("_math.ceil("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "truncate" -> { sb.append("int("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "round"  -> { sb.append("round("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "sqrt"   -> { sb.append("_math.sqrt("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "sin"    -> { sb.append("_math.sin("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "cos"    -> { sb.append("_math.cos("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "tan"    -> { sb.append("_math.tan("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "asin"   -> { sb.append("_math.asin("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "acos"   -> { sb.append("_math.acos("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "atan"   -> { if(args.size()==1){sb.append("_math.atan(");apE(sb,args.get(0),ind);}else{sb.append("_math.atan2(");apE(sb,args.get(0),ind);sb.append(", ");apE(sb,args.get(1),ind);} sb.append(")"); return sb.toString(); }
            case "log"    -> { sb.append("_math.log("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "exp"    -> { sb.append("_math.exp("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "gcd"    -> { sb.append("_math.gcd("); apAll(sb,args,ind); sb.append(")"); return sb.toString(); }
            case "bitwise-and","logand","bit-and" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" & "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "bitwise-or","logior","bit-or"   -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" | "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "bitwise-xor","logxor","bit-xor" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" ^ "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "bitwise-not","lognot","bit-not" -> { sb.append("(~"); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "arithmetic-shift","ash" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" << "); apE(sb,args.get(1),ind); sb.append(" if ("); apE(sb,args.get(1),ind); sb.append(") >= 0 else "); apE(sb,args.get(0),ind); sb.append(" >> -("); apE(sb,args.get(1),ind); sb.append("))"); return sb.toString(); }
        }
        return null;
    }

    // ── Comparison / predicates ───────────────────────────────────────────────

    private String cmpOp(String n, List<SExpr> args, int ind) throws SchemeException {
        StringBuilder sb = new StringBuilder();
        switch (n) {
            case "=","equal-number?" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" == "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "<"  -> { return chain(args, " < ",  ind); }
            case ">"  -> { return chain(args, " > ",  ind); }
            case "<=" -> { return chain(args, " <= ", ind); }
            case ">=" -> { return chain(args, " >= ", ind); }
            case "equal?","eqv?" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" == "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "eq?"           -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" is "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "zero?"    -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" == 0)"); return sb.toString(); }
            case "positive?"-> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" > 0)"); return sb.toString(); }
            case "negative?"-> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" < 0)"); return sb.toString(); }
            case "even?"    -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" % 2 == 0)"); return sb.toString(); }
            case "odd?"     -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" % 2 != 0)"); return sb.toString(); }
            case "null?","nil?" -> { sb.append("(("); apE(sb,args.get(0),ind); sb.append(") == [])"); return sb.toString(); }
            case "pair?"    -> { sb.append("(isinstance("); apE(sb,args.get(0),ind); sb.append(", list) and len("); apE(sb,args.get(0),ind); sb.append(") > 0)"); return sb.toString(); }
            case "list?"    -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", list)"); return sb.toString(); }
            case "number?"  -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", (int, float))"); return sb.toString(); }
            case "string?"  -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", str)"); return sb.toString(); }
            case "boolean?" -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", bool)"); return sb.toString(); }
            case "procedure?","callable?" -> { sb.append("callable("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "integer?" -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", int)"); return sb.toString(); }
            case "char?"    -> { sb.append("(isinstance("); apE(sb,args.get(0),ind); sb.append(", str) and len("); apE(sb,args.get(0),ind); sb.append(") == 1)"); return sb.toString(); }
            case "vector?"  -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", list)"); return sb.toString(); }
        }
        return null;
    }

    private String chain(List<SExpr> args, String op, int ind) throws SchemeException {
        StringBuilder sb = new StringBuilder("("); apE(sb,args.get(0),ind);
        for (int i=1;i<args.size();i++){sb.append(op);apE(sb,args.get(i),ind);}
        sb.append(")"); return sb.toString();
    }

    // ── List ops ──────────────────────────────────────────────────────────────

    private String listOp(String n, List<SExpr> args, int ind) throws SchemeException {
        StringBuilder sb = new StringBuilder();
        switch (n) {
            case "cons"   -> { sb.append("(["); apE(sb,args.get(0),ind); sb.append("] + "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "car","first"   -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")[0]"); return sb.toString(); }
            case "cdr","rest"    -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")[1:]"); return sb.toString(); }
            case "cadr","second" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")[1]"); return sb.toString(); }
            case "caddr","third" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")[2]"); return sb.toString(); }
            case "cadddr","fourth" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")[3]"); return sb.toString(); }
            case "cddr"   -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")[2:]"); return sb.toString(); }
            case "list"   -> { sb.append("["); apAll(sb,args,ind); sb.append("]"); return sb.toString(); }
            case "list-ref" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")["); apE(sb,args.get(1),ind); sb.append("]"); return sb.toString(); }
            case "list-tail" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")["); apE(sb,args.get(1),ind); sb.append(":]"); return sb.toString(); }
            case "length" -> { sb.append("len("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "append" -> {
                if (args.isEmpty()) return "[]";
                sb.append("("); apE(sb,args.get(0),ind);
                for (int i=1;i<args.size();i++){sb.append(" + ");apE(sb,args.get(i),ind);}
                sb.append(")"); return sb.toString();
            }
            case "reverse" -> { sb.append("list(reversed("); apE(sb,args.get(0),ind); sb.append("))"); return sb.toString(); }
            case "list-copy" -> { sb.append("list("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "assoc","assq","assv" -> { sb.append("next((_p for _p in "); apE(sb,args.get(1),ind); sb.append(" if _p[0] == "); apE(sb,args.get(0),ind); sb.append("), False)"); return sb.toString(); }
            case "member","memq","memv" -> { sb.append("([_i for _i in "); apE(sb,args.get(1),ind); sb.append(" if _i == "); apE(sb,args.get(0),ind); sb.append("] or False)"); return sb.toString(); }
            case "sort","list-sort" -> { sb.append("sorted("); apE(sb,args.get(1),ind); sb.append(", key="); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
        }
        return null;
    }

    // ── Quote / quasiquote ────────────────────────────────────────────────────

    private void emitQuote(SExpr d) throws SchemeException {
        if (d instanceof SExpr.Nil)     emit("[]");
        else if (d instanceof SExpr.Bool b)  emit(b.value() ? "True" : "False");
        else if (d instanceof SExpr.Num n)   emit(n.raw());
        else if (d instanceof SExpr.Str s)   emitStr(s.value());
        else if (d instanceof SExpr.Sym s)   emitStr(s.name());
        else if (d instanceof SExpr.Char c)  emit("'" + esc(c.toJavaChar()) + "'");
        else if (d instanceof SExpr.Vec v)   { emit("["); for(int i=0;i<v.elements().size();i++){if(i>0)emit(", ");emitQuote(v.elements().get(i));}emit("]"); }
        else if (d instanceof SExpr.Pair p)  { emit("["); List<SExpr> items=p.toList(); for(int i=0;i<items.size();i++){if(i>0)emit(", ");emitQuote(items.get(i));}emit("]"); }
    }

    private void emitQQ(SExpr d, int ind) throws SchemeException {
        if (d.isForm("unquote")) { emitExpr(nth(d,1), ind); return; }
        if (d instanceof SExpr.Pair p) {
            List<SExpr> items = p.toList();
            boolean hasSplice = items.stream().anyMatch(e -> e.isForm("unquote-splicing"));
            if (hasSplice) {
                emit("(");
                for (SExpr item : items) {
                    if (item.isForm("unquote-splicing")) { emitExpr(nth(item,1), ind); }
                    else { emit("["); emitQQ(item, ind); emit("] + "); }
                }
                emit("[])\n");
            } else {
                emit("["); for(int i=0;i<items.size();i++){if(i>0)emit(", ");emitQQ(items.get(i),ind);} emit("]");
            }
            return;
        }
        emitQuote(d);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void emitArgs(List<SExpr> args, int ind) throws SchemeException {
        for (int i=0;i<args.size();i++){if(i>0)emit(", ");emitExpr(args.get(i),ind);}
    }

    private void emitStr(String s) {
        emit("\"");
        for (char c : s.toCharArray()) {
            if (c=='"') emit("\\\""); else if (c=='\\') emit("\\\\");
            else if (c=='\n') emit("\\n"); else if (c=='\t') emit("\\t");
            else emit(String.valueOf(c));
        }
        emit("\"");
    }

    private String esc(char c) {
        return switch (c) {
            case '\'' -> "\\'"; case '\\' -> "\\\\";
            case '\n' -> "\\n"; case '\t' -> "\\t"; default -> String.valueOf(c);
        };
    }

    private void emit(String s)    { out.append(s); }
    private void indent(int n)     { out.append("    ".repeat(n)); }

    private void marker(int scmLine, int ind) {
        if (scmLine > 0 && scmLine != lastScmLine) {
            out.append("    ".repeat(ind)).append("# scm:").append(scmLine).append("\n");
            lastScmLine = scmLine;
        }
    }

    private Transpiler subTx() {
        Transpiler t = new Transpiler();
        t.inFunction = this.inFunction;
        t.globalVars.addAll(this.globalVars);
        t.localVars.addAll(this.localVars);
        return t;
    }

    private void apE(StringBuilder sb, SExpr e, int ind) throws SchemeException {
        Transpiler sub = subTx(); sub.emitExpr(e, ind); sb.append(sub.out);
    }

    private void apAll(StringBuilder sb, List<SExpr> args, int ind) throws SchemeException {
        for (int i=0;i<args.size();i++){if(i>0)sb.append(", ");apE(sb,args.get(i),ind);}
    }

    private boolean isSimple(SExpr e) {
        if (!(e instanceof SExpr.Pair)) return true;
        String h = ((SExpr.Pair)e).headSym();
        return h == null || !Set.of("define","set!","begin","if","cond","when","unless","let","let*","letrec","do","for-each","with-matrix").contains(h);
    }

    /** Collect all set! target names in a list of forms (recursive). */
    private Set<String> findSetTargets(List<SExpr> forms) {
        Set<String> targets = new LinkedHashSet<>();
        for (SExpr f : forms) collectSetTargets(f, targets);
        return targets;
    }

    private void collectSetTargets(SExpr expr, Set<String> targets) {
        if (!(expr instanceof SExpr.Pair p)) return;
        if (p.car().isSym("set!")) {
            List<SExpr> l = p.toList();
            if (l.size() >= 2 && l.get(1) instanceof SExpr.Sym s) targets.add(snake(s.name()));
        }
        // Recurse into all children
        SExpr cur = p;
        while (cur instanceof SExpr.Pair pp) { collectSetTargets(pp.car(), targets); cur = pp.cdr(); }
    }

    static String snake(String name) {
        if (name == null) return "_";
        return switch (name) {
            case "display" -> "print"; case "length" -> "len";
            default -> {
                String s = name.replace('-', '_');
                if (s.endsWith("?")) s = s.substring(0, s.length()-1) + "_p";
                if (s.endsWith("!")) s = s.substring(0, s.length()-1);
                if (s.startsWith("#")) s = s.substring(1);
                yield s;
            }
        };
    }

    static String pascal(String name) {
        StringBuilder sb = new StringBuilder();
        for (String p : name.split("[-_]")) if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return sb.toString();
    }

    private List<SExpr> list(SExpr e) {
        if (e instanceof SExpr.Pair p) return p.toList();
        if (e instanceof SExpr.Nil) return List.of();
        return List.of(e);
    }

    private SExpr nth(SExpr e, int n) throws SchemeException {
        List<SExpr> l = list(e);
        if (n >= l.size()) throw new SchemeException("expected element " + n, e.line());
        return l.get(n);
    }

    private String sym(SExpr e) throws SchemeException {
        if (e instanceof SExpr.Sym s) return s.name();
        throw new SchemeException("expected symbol", e.line());
    }

    private String pyStrList(List<String> items) {
        return "['" + String.join("', '", items) + "']";
    }
}

