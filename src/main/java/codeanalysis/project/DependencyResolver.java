package codeanalysis.project;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves Maven dependencies from [dependencies] in siyo.toml.
 * Downloads JARs from Maven Central to ~/.siyo/cache/ if not already cached.
 */
public class DependencyResolver {
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    private final Path _cacheDir;

    public DependencyResolver() {
        _cacheDir = Path.of(System.getProperty("user.home"), ".siyo", "cache");
    }

    /**
     * Resolve all dependencies, downloading missing JARs. Returns list of JAR paths.
     */
    public List<Path> resolve(Map<String, String> dependencies) {
        List<Path> jars = new ArrayList<>();

        for (var entry : dependencies.entrySet()) {
            String coordinate = entry.getKey();  // "org.xerial:sqlite-jdbc"
            String version = entry.getValue();     // "3.45.0"

            String[] parts = coordinate.split(":");
            if (parts.length != 2) {
                System.err.println("Invalid dependency coordinate: " + coordinate);
                continue;
            }

            String groupId = parts[0];
            String artifactId = parts[1];
            Path jarPath = getJarPath(groupId, artifactId, version);

            if (!Files.exists(jarPath)) {
                if (!download(groupId, artifactId, version, jarPath)) {
                    System.err.println("Failed to download: " + coordinate + ":" + version);
                    continue;
                }
            }

            jars.add(jarPath);
        }

        return jars;
    }

    private Path getJarPath(String groupId, String artifactId, String version) {
        // ~/.siyo/cache/org/xerial/sqlite-jdbc/3.45.0/sqlite-jdbc-3.45.0.jar
        String groupPath = groupId.replace('.', '/');
        return _cacheDir.resolve(groupPath)
                .resolve(artifactId)
                .resolve(version)
                .resolve(artifactId + "-" + version + ".jar");
    }

    private boolean download(String groupId, String artifactId, String version, Path target) {
        // https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.0/sqlite-jdbc-3.45.0.jar
        String groupPath = groupId.replace('.', '/');
        String url = String.format("%s/%s/%s/%s/%s-%s.jar",
                MAVEN_CENTRAL, groupPath, artifactId, version, artifactId, version);

        System.out.println("Downloading " + artifactId + "-" + version + ".jar ...");

        try {
            Files.createDirectories(target.getParent());
            URL remote = new URL(url);
            try (InputStream in = remote.openStream();
                 OutputStream out = Files.newOutputStream(target)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Download error: " + e.getMessage());
            // Clean up partial download
            try { Files.deleteIfExists(target); } catch (Exception ignored) {}
            return false;
        }
    }
}
