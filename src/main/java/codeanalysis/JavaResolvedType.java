package codeanalysis;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a resolved Java type with generic type argument bindings.
 * e.g., Set<SelectionKey> → classInfo=Set, typeArgs={E: resolvedType(SelectionKey)}
 */
public class JavaResolvedType {
    private final JavaClassInfo _classInfo;
    private final Map<String, JavaResolvedType> _typeArgs; // type param name → bound type

    public JavaResolvedType(JavaClassInfo classInfo) {
        _classInfo = classInfo;
        _typeArgs = new HashMap<>();
    }

    public JavaResolvedType(JavaClassInfo classInfo, Map<String, JavaResolvedType> typeArgs) {
        _classInfo = classInfo;
        _typeArgs = typeArgs != null ? typeArgs : new HashMap<>();
    }

    public JavaClassInfo getClassInfo() { return _classInfo; }
    public Map<String, JavaResolvedType> getTypeArgs() { return _typeArgs; }

    /**
     * Resolve a type variable name to its bound type.
     * e.g., for Set<SelectionKey>, resolveTypeVar("E") → SelectionKey
     */
    public JavaResolvedType resolveTypeVar(String typeVarName) {
        return _typeArgs.get(typeVarName);
    }

    public String getFullName() {
        return _classInfo != null ? _classInfo.getFullName() : "?";
    }
}
