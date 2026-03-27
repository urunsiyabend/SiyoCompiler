package codeanalysis;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * Manages external classpath JARs for compile-time and runtime class resolution.
 * Allows loading .class metadata from external JARs via ASM.
 */
public class JavaClasspath {
    private static final List<String> _jarPaths = new ArrayList<>();
    private static URLClassLoader _classLoader = null;

    /**
     * Add JAR path(s) to the classpath. Supports path separator (; on Windows, : on Unix).
     */
    public static void addClasspath(String classpath) {
        String separator = System.getProperty("path.separator");
        for (String path : classpath.split(separator.equals("") ? ";" : separator)) {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                _jarPaths.add(trimmed);
            }
        }
        _classLoader = null; // reset to force rebuild
    }

    /**
     * Get a ClassLoader that includes all external JARs.
     * Used by the interpreter for reflection-based method invocation.
     */
    public static ClassLoader getClassLoader() {
        if (_classLoader == null && !_jarPaths.isEmpty()) {
            try {
                URL[] urls = new URL[_jarPaths.size()];
                for (int i = 0; i < _jarPaths.size(); i++) {
                    urls[i] = new File(_jarPaths.get(i)).toURI().toURL();
                }
                _classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
            } catch (Exception e) {
                return ClassLoader.getSystemClassLoader();
            }
        }
        return _classLoader != null ? _classLoader : ClassLoader.getSystemClassLoader();
    }

    /**
     * Find a .class file as InputStream from external JARs.
     * Used by JavaClassMetadata for ASM-based compile-time resolution.
     */
    public static InputStream findClass(String resourcePath) {
        ClassLoader cl = getClassLoader();
        return cl.getResourceAsStream(resourcePath);
    }

    /**
     * Get the classpath string for java -cp when running compiled classes.
     */
    public static String getClasspathString() {
        if (_jarPaths.isEmpty()) return ".";
        StringBuilder sb = new StringBuilder(".");
        String sep = System.getProperty("path.separator");
        for (String path : _jarPaths) {
            sb.append(sep).append(path);
        }
        return sb.toString();
    }

    public static List<String> getJarPaths() {
        return _jarPaths;
    }
}
