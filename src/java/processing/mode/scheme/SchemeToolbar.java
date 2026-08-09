package processing.mode.scheme;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import processing.app.ui.Editor;
import processing.app.ui.EditorButton;
import processing.app.ui.EditorToolbar;

public class SchemeToolbar extends EditorToolbar {

    private final SchemeEditor schemeEditor;

    public SchemeToolbar(Editor editor) {
        super(editor);
        this.schemeEditor = (SchemeEditor) editor;
    }

    @Override
    public void handleRun(int modifiers) {
        schemeEditor.handleRun();
    }

    @Override
    public void handleStop() {
        schemeEditor.handleStop();
    }

    @Override
    public List<EditorButton> createButtons() {
        List<EditorButton> buttons = new ArrayList<>();
        runButton = new EditorButton(this,
                "/lib/toolbar/run", "Run", "Run") {
            @Override public void actionPerformed(ActionEvent e) {
                handleRun(e.getModifiers());
            }
        };
        buttons.add(runButton);
        stopButton = new EditorButton(this,
                "/lib/toolbar/stop", "Stop") {
            @Override public void actionPerformed(ActionEvent e) {
                handleStop();
            }
        };
        buttons.add(stopButton);
        return buttons;
    }
}
