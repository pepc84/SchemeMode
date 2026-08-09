package processing.mode.scheme;

import processing.app.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public class SchemeBuild {
    private final Sketch     sketch;
    private final SchemeMode mode;
    public PreparedLaunch launch;
    public LineMapper     lineMapper;

    public SchemeBuild(Sketch sketch, SchemeMode mode) {
        this.sketch = sketch; this.mode = mode;
    }

    public boolean prepare(RunnerListener listener) {
        if (!sketch.isReadOnly()) {
            try { sketch.save(); } catch (IOException e) {
                listener.statusError("Cannot save: " + e.getMessage()); return false;
            }
        }

        File primary = primaryFile();
        if (primary == null || !primary.exists()) {
            listener.statusError("No .scm file found."); return false;
        }

        String src;
        try { src = Files.readString(primary.toPath()); } catch (IOException e) {
            listener.statusError("Cannot read sketch: " + e.getMessage()); return false;
        }

        List<SExpr> forms;
        try { forms = Parser.parse(src); } catch (SchemeException e) {
            listener.statusError("Parse error line " + e.line + ": " + e.getMessage()); return false;
        }

        String py;
        try { py = new Transpiler().transpile(forms, sketch.getName()); } catch (SchemeException e) {
            listener.statusError("Transpile error line " + e.line + ": " + e.getMessage()); return false;
        }

        // Match Processing Java default camera:
        // eye at (width/2, height/2, (height/2)/tan(60*pi/360))
        // looking at (width/2, height/2, 0), up (0,1,0)
        py = py.replace("    mode_3d()\n",
            "    mode_3d()\n" +
            "    _eye_z = (height/2) / _math.tan(60 * _math.pi / 360)\n" +
            "    camera_position(width/2, height/2, _eye_z)\n" +
            "    camera_look_at(width/2, height/2, 0)\n");
        lineMapper = new LineMapper(py);

        File buildDir = new File(sketch.getFolder(), "build");
        buildDir.mkdirs();
        File pyFile = new File(buildDir, sketch.getName() + ".py");
        try { Files.writeString(pyFile.toPath(), py); } catch (IOException e) {
            listener.statusError("Cannot write Python: " + e.getMessage()); return false;
        }

        launch = new PreparedLaunch(pyFile, primary.getName(),
                                    mode.getPythonExecutable(), sketch.getName());
        return true;
    }

    private File primaryFile() {
        for (SketchCode c : sketch.getCode())
            if (c.getExtension().equals("scm")) return c.getFile();
        return sketch.getCodeCount() > 0 ? sketch.getCode(0).getFile() : null;
    }

    public static class PreparedLaunch {
        public final File pythonScript;
        public final String sketchFileName, pythonExe, sketchName;
        PreparedLaunch(File s, String sfn, String exe, String name) {
            pythonScript = s; sketchFileName = sfn; pythonExe = exe; sketchName = name;
        }
    }
}
