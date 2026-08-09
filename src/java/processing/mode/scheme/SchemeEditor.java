package processing.mode.scheme;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import processing.app.*;
import processing.app.syntax.*;
import processing.app.ui.*;

public class SchemeEditor extends Editor {

    private SchemeRunner currentRunner;

    public SchemeEditor(Base base, String path,
                        EditorState state, Mode mode) throws EditorException {
        super(base, path, state, mode);
    }

    // ── Text area with PdeTextArea for line numbers + gutter ─────────────────
    @Override
    protected JEditTextArea createTextArea() {
        return new PdeTextArea(new PdeTextAreaDefaults(),
                               new SchemeInputHandler(this), this);
    }

    // ── Required abstract overrides (exact CppEditor pattern) ─────────────────
    @Override public EditorToolbar createToolbar()               { return new SchemeToolbar(this); }
    @Override public Formatter     createFormatter()             { return null; }
    @Override public String        getCommentPrefix()            { return ";"; }
    @Override public void          handleImportLibrary(String n) { }
    @Override public void          startIndeterminate()          { }
    @Override public void          stopIndeterminate()           { }
    @Override public void          statusHalt()                  { }
    @Override public boolean       isHalted()                    { return currentRunner == null; }
    @Override public void          deactivateRun()               { toolbar.deactivateRun(); }

    @Override
    public void internalCloseRunner() {
        if (currentRunner != null) { currentRunner.stop(); currentRunner = null; }
    }

    // ── Menus ─────────────────────────────────────────────────────────────────
    @Override
    public JMenu buildFileMenu() {
        return buildFileMenu(new JMenuItem[]{});
    }

    @Override
    public JMenu buildHelpMenu() {
        return new JMenu("Help");
    }

    @Override
    public JMenu buildSketchMenu() {
        JMenuItem run  = processing.app.ui.Toolkit.newJMenuItem("Run", 'R');
        JMenuItem stop = new JMenuItem("Stop");
        run.addActionListener(e  -> handleRun());
        stop.addActionListener(e -> handleStop());
        return buildSketchMenu(new JMenuItem[]{ run, stop });
    }

    // ── Run / Stop ────────────────────────────────────────────────────────────
    public void handleRun() {
        internalCloseRunner();
        clearErrorHighlight();
        SchemeMode mode = (SchemeMode) getMode();
        // Skip read-only check — we run from generated Python, not the source file
        if (!mode.isMewnalaAvailable()) {
            statusError("mewnala not found. Run: pip install mewnala");
            deactivateRun();
            return;
        }
        statusNotice("Running...");
        new Thread(() -> {
            currentRunner = mode.handleLaunch(sketch, makeListener());
            if (currentRunner == null) {
                EventQueue.invokeLater(this::deactivateRun);
            }
        }, "scheme-runner").start();
    }

    public void handleStop() {
        internalCloseRunner();
        deactivateRun();
        statusNotice("Stopped.");
    }

    void onSketchStopped() {
        currentRunner = null;
        EventQueue.invokeLater(this::deactivateRun);
    }

    // ── Error reporting with gutter line numbers (CppEditor pattern) ──────────
    void reportError(SchemeError err) {
        EventQueue.invokeLater(() -> {
            if (err.line >= 1 && textarea instanceof PdeTextArea) {
                PdeTextArea pta = (PdeTextArea) textarea;
                try {
                    int docLine = err.line - 1;
                    pta.clearGutterText();
                    pta.setGutterText(docLine, "▶");
                    pta.scrollTo(docLine, 0);
                    pta.select(
                        pta.getLineStartOffset(docLine),
                        pta.getLineStopOffset(docLine) - 1
                    );
                } catch (Exception ignored) {}
                statusError(err.message + "  (line " + err.line + ")");
            } else {
                statusError(err.message);
            }
        });
    }

    void clearErrorHighlight() {
        if (textarea instanceof PdeTextArea) {
            ((PdeTextArea) textarea).clearGutterText();
        }
    }

    // ── Comment toggle (Scheme: use ; prefix) ─────────────────────────────────
    void toggleComment(JEditTextArea ta) {
        int selStart  = ta.getSelectionStart();
        int selEnd    = ta.getSelectionStop();
        int startLine = ta.getLineOfOffset(selStart);
        int endLine   = ta.getLineOfOffset(selEnd);
        if (endLine > startLine && ta.getLineStartOffset(endLine) == selEnd) endLine--;

        boolean allCommented = true;
        for (int i = startLine; i <= endLine; i++) {
            if (!getLineText(ta, i).stripLeading().startsWith(";")) {
                allCommented = false; break;
            }
        }
        for (int i = endLine; i >= startLine; i--) {
            String raw = getLineText(ta, i);
            if (allCommented)
                replaceLineText(ta, i, raw.replaceFirst("(\\s*);\\s?", "$1"));
            else {
                int indent = 0;
                while (indent < raw.length() &&
                       (raw.charAt(indent)==' ' || raw.charAt(indent)=='\t')) indent++;
                replaceLineText(ta, i,
                    raw.substring(0, indent) + "; " + raw.substring(indent));
            }
        }
    }

    private String getLineText(JEditTextArea ta, int line) {
        try {
            int start = ta.getLineStartOffset(line);
            int end   = ta.getLineStopOffset(line) - 1;
            return ta.getDocument().getText(start, Math.max(0, end - start));
        } catch (javax.swing.text.BadLocationException e) { return ""; }
    }

    private void replaceLineText(JEditTextArea ta, int line, String text) {
        try {
            int start = ta.getLineStartOffset(line);
            int end   = ta.getLineStopOffset(line) - 1;
            ta.getDocument().remove(start, Math.max(0, end - start));
            ta.getDocument().insertString(start, text, null);
        } catch (javax.swing.text.BadLocationException e) {}
    }

    // ── RunnerListener ────────────────────────────────────────────────────────
    private RunnerListener makeListener() {
        return new RunnerListener() {
            @Override public void statusError(String m)    { SchemeEditor.this.statusError(m); }
            @Override public void statusError(Exception e) { SchemeEditor.this.statusError(e.getMessage()); }
            @Override public void statusNotice(String m)   { SchemeEditor.this.statusNotice(m); }
            @Override public void statusHalt()             { SchemeEditor.this.onSketchStopped(); }
            @Override public boolean isHalted()            { return currentRunner == null; }
            @Override public void startIndeterminate()     { }
            @Override public void stopIndeterminate()      { }
        };
    }
}
