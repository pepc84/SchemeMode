package processing.mode.scheme;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import processing.app.Preferences;
import processing.app.Sketch;
import processing.app.syntax.JEditTextArea;
import processing.app.syntax.PdeInputHandler;
import processing.app.ui.Editor;

/**
 * SchemeInputHandler — port of CppInputHandler for Scheme mode.
 * Provides auto-indent, tab handling, and Ctrl+; comment toggle.
 */
public class SchemeInputHandler extends PdeInputHandler {

    public SchemeInputHandler(Editor editor) { super(editor); }

    @Override
    public boolean handlePressed(KeyEvent event) {
        char c = event.getKeyChar();
        int code = event.getKeyCode();
        Sketch sketch = editor.getSketch();
        JEditTextArea textarea = editor.getTextArea();

        if (event.isMetaDown()) return false;

        if ((code == KeyEvent.VK_BACK_SPACE) || (code == KeyEvent.VK_TAB) ||
            (code == KeyEvent.VK_ENTER) || ((c >= 32) && (c < 128))) {
            sketch.setModified(true);
        }

        // Ctrl+; : toggle line comment (Scheme style)
        if (event.isControlDown() && (code == KeyEvent.VK_SLASH)) {
            if (editor instanceof SchemeEditor) {
                ((SchemeEditor) editor).toggleComment(textarea);
            }
            event.consume();
            return true;
        }

        // Ctrl+Up: jump to previous blank line
        if ((code == KeyEvent.VK_UP) && event.isControlDown()) {
            char[] contents = textarea.getText().toCharArray();
            int caretIndex = textarea.getCaretPosition();
            int index = calcLineStart(caretIndex - 1, contents);
            index -= 2;
            boolean onlySpaces = true;
            while (index > 0) {
                if (contents[index] == 10) {
                    if (onlySpaces) { index++; break; }
                    else onlySpaces = true;
                } else if (contents[index] != ' ') onlySpaces = false;
                index--;
            }
            if (index < 0) index = 0;
            if (event.isShiftDown()) { textarea.setSelectionStart(caretIndex); textarea.setSelectionEnd(index); }
            else textarea.setCaretPosition(index);
            event.consume();

        // Ctrl+Down: jump to next blank line
        } else if ((code == KeyEvent.VK_DOWN) && event.isControlDown()) {
            char[] contents = textarea.getText().toCharArray();
            int caretIndex = textarea.getCaretPosition();
            int index = caretIndex, lineStart = 0;
            boolean onlySpaces = false;
            while (index < contents.length) {
                if (contents[index] == 10) {
                    if (onlySpaces) { index = lineStart; break; }
                    else { lineStart = index + 1; onlySpaces = true; }
                } else if (contents[index] != ' ') onlySpaces = false;
                index++;
            }
            if (event.isShiftDown()) { textarea.setSelectionStart(caretIndex); textarea.setSelectionEnd(index); }
            else textarea.setCaretPosition(index);
            event.consume();

        // Tab / Shift+Tab
        } else if (c == 9) {
            if (event.isShiftDown()) editor.handleOutdent();
            else if (textarea.isSelectionActive()) editor.handleIndent();
            else if (Preferences.getBoolean("editor.tabs.expand")) {
                textarea.setSelectedText(spaces(Preferences.getInteger("editor.tabs.size")));
                event.consume();
            } else { textarea.setSelectedText("\t"); event.consume(); }

        // Enter: auto-indent
        } else if (code == 10 || code == 13) {
            if (Preferences.getBoolean("editor.indent")) {
                char[] contents = textarea.getText().toCharArray();
                int tabSize = Preferences.getInteger("editor.tabs.size");
                int origIndex = textarea.getCaretPosition() - 1;
                int spaceCount = calcSpaceCount(origIndex, contents);
                int index2 = origIndex;
                while (index2 >= 0 && Character.isWhitespace(contents[index2])) index2--;
                if (index2 != -1 && contents[index2] == '(') {
                    spaceCount = calcSpaceCount(index2, contents) + tabSize;
                }
                int index = origIndex + 1, extraCount = 0;
                while (index < contents.length && contents[index] == ' ') { extraCount++; index++; }
                spaceCount -= extraCount;
                if (spaceCount < 0) {
                    textarea.setSelectionEnd(textarea.getSelectionStop() - spaceCount);
                    textarea.setSelectedText("\n");
                    textarea.setCaretPosition(textarea.getCaretPosition() + extraCount + spaceCount);
                } else {
                    textarea.setSelectedText("\n" + spaces(spaceCount));
                    textarea.setCaretPosition(textarea.getCaretPosition() + extraCount);
                }
            } else {
                textarea.setSelectedText(String.valueOf(c));
            }
            event.consume();
        }
        return false;
    }

    @Override
    public boolean handleTyped(KeyEvent event) {
        char c = event.getKeyChar();
        if (event.isControlDown()) {
            if (c == KeyEvent.VK_COMMA || c == KeyEvent.VK_SPACE) {
                event.consume(); return true;
            }
        }
        return false;
    }

    private int calcLineStart(int index, char[] contents) {
        while (index != -1 && contents[index] != 10 && contents[index] != 13) index--;
        return index + 1;
    }

    private int calcSpaceCount(int index, char[] contents) {
        index = calcLineStart(index, contents);
        int count = 0;
        while (index < contents.length && index >= 0 && contents[index++] == ' ') count++;
        return count;
    }

    private static String spaces(int count) {
        char[] c = new char[count]; Arrays.fill(c, ' '); return new String(c);
    }
}
