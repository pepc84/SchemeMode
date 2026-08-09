package processing.mode.scheme;

import processing.app.*;
import processing.app.ui.*;
import java.io.File;

public class SchemeMode extends Mode {

    public SchemeMode(Base base, File folder) { super(base, folder); }

    @Override public String   getTitle()            { return "Scheme"; }
    @Override public String   getDefaultExtension() { return "scm"; }
    @Override public String[] getExtensions()       { return new String[]{"scm","sch","pde"}; }
    @Override public String[] getIgnorable()        { return new String[]{"scm~","pde~","build","__pycache__"}; }

    @Override
    public File[] getExampleCategoryFolders() {
        return new File[]{
            new File(examplesFolder, "Basics"),
            new File(examplesFolder, "Topics"),
            new File(examplesFolder, "3D"),
        };
    }

    @Override
    public Editor createEditor(Base base, String path,
                               EditorState state) throws EditorException {
        return new SchemeEditor(base, path, state, this);
    }

    public SchemeRunner handleLaunch(Sketch sketch, RunnerListener listener) {
        SchemeBuild build = new SchemeBuild(sketch, this);
        if (!build.prepare(listener)) return null;
        SchemeRunner runner = new SchemeRunner(build, listener);
        new Thread(runner::launch, "scheme-runner").start();
        return runner;
    }

    public void handleStop(SchemeRunner runner) {
        if (runner != null) runner.stop();
    }

    public File getModeJarFolder() { return new File(getFolder(), "mode"); }

    @Override
    protected processing.app.syntax.TokenMarker createTokenMarker() {
        return new SchemeTokenMarker();
    }

    @Override
    public File[] getKeywordFiles() {
        return new File[]{ new File(getFolder(), "resources/keywords.txt") };
    }

    public String getPythonExecutable() {
        for (String rel : new String[]{"venv/bin/python3", ".venv/bin/python3"}) {
            File f = new File(getFolder(), rel);
            if (f.exists()) return f.getAbsolutePath();
        }
        return "python3";
    }

    public boolean isMewnalaAvailable() {
        try {
            Process p = new ProcessBuilder(getPythonExecutable(), "-c", "import mewnala")
                .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) { return false; }
    }
}
