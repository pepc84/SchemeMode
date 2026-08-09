package processing.mode.scheme;

import processing.app.syntax.*;

/**
 * Token marker that guards against null keywordColoring.
 * The base PdeTokenMarker crashes if keywordColoring is null
 * when the editor opens before keywords are loaded.
 */
public class SchemeTokenMarker extends PdeTokenMarker {

    @Override
    public byte markTokensImpl(byte token, javax.swing.text.Segment line, int lineIndex) {
        // Guard: if keywords not loaded yet, initialize empty map
        if (keywordColoring == null) {
            keywordColoring = new KeywordMap(false);
        }
        return super.markTokensImpl(token, line, lineIndex);
    }
}
