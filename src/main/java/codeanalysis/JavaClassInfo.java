package codeanalysis;

/**
 * Holds metadata about an imported Java class.
 * Uses compile-time ASM metadata instead of runtime reflection.
 */
public class JavaClassInfo {
    private final String _simpleName;
    private final String _fullName;
    private final String _internalName;
    private final JavaClassMetadata _metadata;

    public JavaClassInfo(String simpleName, String fullName, JavaClassMetadata metadata) {
        _simpleName = simpleName;
        _fullName = fullName;
        _internalName = fullName.replace('.', '/');
        _metadata = metadata;
    }

    public String getSimpleName() { return _simpleName; }
    public String getFullName() { return _fullName; }
    public String getInternalName() { return _internalName; }
    public JavaClassMetadata getMetadata() { return _metadata; }

    public JavaMethodSignature resolveMethod(String name, int argCount) {
        return _metadata.resolveMethod(name, argCount);
    }

    public JavaMethodSignature resolveMethod(String name, int argCount, Class<?>[] argTypes) {
        return _metadata.resolveMethod(name, argCount, argTypes);
    }

    public JavaMethodSignature resolveConstructor(int argCount) {
        return _metadata.resolveConstructor(argCount);
    }
}
