package codeanalysis;

/**
 * Holds metadata about an imported Java class.
 */
public class JavaClassInfo {
    private final String _simpleName;     // "File"
    private final String _fullName;       // "java.io.File"
    private final String _internalName;   // "java/io/File"
    private final Class<?> _javaClass;    // loaded Class for reflection (interpreter)

    public JavaClassInfo(String simpleName, String fullName, Class<?> javaClass) {
        _simpleName = simpleName;
        _fullName = fullName;
        _internalName = fullName.replace('.', '/');
        _javaClass = javaClass;
    }

    public String getSimpleName() { return _simpleName; }
    public String getFullName() { return _fullName; }
    public String getInternalName() { return _internalName; }
    public Class<?> getJavaClass() { return _javaClass; }
}
