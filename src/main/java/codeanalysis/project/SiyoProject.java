package codeanalysis.project;

import codeanalysis.JavaClasspath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Represents a Siyo project defined by siyo.toml.
 * Handles project configuration, dependency resolution, and module root detection.
 */
public class SiyoProject {
    private final Path _projectRoot;
    private final TomlParser _config;
    private final String _name;
    private final String _version;
    private final String _main;

    private static SiyoProject _current; // active project (set during run)

    private SiyoProject(Path projectRoot, TomlParser config) {
        _projectRoot = projectRoot;
        _config = config;
        _name = config.getString("project", "name", "untitled");
        _version = config.getString("project", "version", "0.2.0");
        _main = config.getString("project", "main", "src/main.siyo");
    }

    /**
     * Try to load a project by searching from the given directory upward for siyo.toml.
     * Returns null if no siyo.toml found in this directory or any ancestor.
     */
    public static SiyoProject load(Path directory) {
        Path dir = directory.toAbsolutePath().normalize();
        while (dir != null) {
            Path tomlPath = dir.resolve("siyo.toml");
            if (Files.exists(tomlPath)) {
                try {
                    TomlParser config = TomlParser.parse(tomlPath);
                    return new SiyoProject(dir, config);
                } catch (Exception e) {
                    System.err.println("Error reading siyo.toml: " + e.getMessage());
                    return null;
                }
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * Resolve dependencies and add them to the classpath.
     */
    public void resolveDependencies() {
        Map<String, String> deps = _config.getTable("dependencies");
        if (deps.isEmpty()) return;

        DependencyResolver resolver = new DependencyResolver();
        List<Path> jars = resolver.resolve(deps);

        for (Path jar : jars) {
            JavaClasspath.addClasspath(jar.toAbsolutePath().toString());
        }
    }

    /**
     * Get absolute path to the main source file.
     */
    public Path getMainFile() {
        return _projectRoot.resolve(_main);
    }

    public Path getProjectRoot() {
        return _projectRoot;
    }

    /**
     * Get the src/ directory for module resolution.
     */
    public Path getSourceRoot() {
        return _projectRoot.resolve("src");
    }

    public String getName() { return _name; }
    public String getVersion() { return _version; }

    public static void setCurrent(SiyoProject project) { _current = project; }
    public static SiyoProject getCurrent() { return _current; }

    /**
     * Create a new project skeleton.
     */
    public static void createNew(String name) {
        Path projectDir = Path.of(System.getProperty("user.dir"), name);

        if (Files.exists(projectDir)) {
            System.err.println("Error: directory '" + name + "' already exists.");
            System.exit(1);
        }

        try {
            // Create directories
            Files.createDirectories(projectDir.resolve("src"));

            // siyo.toml
            String toml = "[project]\n" +
                    "name = \"" + name + "\"\n" +
                    "version = \"0.2.0\"\n" +
                    "main = \"src/main.siyo\"\n" +
                    "\n" +
                    "[dependencies]\n";
            Files.writeString(projectDir.resolve("siyo.toml"), toml);

            // src/main.siyo
            String main = "fn main() {\n" +
                    "    println(\"Hello from " + name + "!\")\n" +
                    "}\n";
            Files.writeString(projectDir.resolve("src").resolve("main.siyo"), main);

            // .gitignore
            String gitignore = "# Build artifacts\n" +
                    "*.class\n" +
                    "\n" +
                    "# IDE\n" +
                    ".idea/\n" +
                    "*.iml\n" +
                    ".vscode/\n";
            Files.writeString(projectDir.resolve(".gitignore"), gitignore);

            System.out.println("Created project '" + name + "'");
            System.out.println("  " + name + "/siyo.toml");
            System.out.println("  " + name + "/src/main.siyo");
            System.out.println("  " + name + "/.gitignore");
        } catch (Exception e) {
            System.err.println("Error creating project: " + e.getMessage());
            System.exit(1);
        }
    }
}
