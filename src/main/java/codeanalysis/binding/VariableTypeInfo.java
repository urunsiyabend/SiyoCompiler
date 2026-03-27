package codeanalysis.binding;

import codeanalysis.JavaClassInfo;
import codeanalysis.JavaResolvedType;
import codeanalysis.StructSymbol;

/**
 * Tracks additional type metadata for variables beyond their Class<?> type.
 * Covers arrays (element type), structs (struct symbol), and Java objects (class info).
 */
public class VariableTypeInfo {
    private final Class<?> _arrayElementType;
    private final StructSymbol _structType;
    private final StructSymbol _arrayElementStructType;
    private final JavaClassInfo _javaClassType;
    private final JavaResolvedType _javaResolvedType; // parameterized type with generic bindings

    public VariableTypeInfo(Class<?> arrayElementType, StructSymbol structType,
                             StructSymbol arrayElementStructType, JavaClassInfo javaClassType) {
        this(arrayElementType, structType, arrayElementStructType, javaClassType, null);
    }

    public VariableTypeInfo(Class<?> arrayElementType, StructSymbol structType,
                             StructSymbol arrayElementStructType, JavaClassInfo javaClassType,
                             JavaResolvedType javaResolvedType) {
        _arrayElementType = arrayElementType;
        _structType = structType;
        _arrayElementStructType = arrayElementStructType;
        _javaClassType = javaClassType;
        _javaResolvedType = javaResolvedType;
    }

    public static VariableTypeInfo forJavaResolvedType(JavaResolvedType resolvedType) {
        return new VariableTypeInfo(null, null, null,
                resolvedType.getClassInfo(), resolvedType);
    }

    public static VariableTypeInfo forArray(Class<?> elementType) {
        return new VariableTypeInfo(elementType, null, null, null);
    }

    public static VariableTypeInfo forArray(Class<?> elementType, StructSymbol elementStructType) {
        return new VariableTypeInfo(elementType, null, elementStructType, null);
    }

    public static VariableTypeInfo forStruct(StructSymbol structType) {
        return new VariableTypeInfo(null, structType, null, null);
    }

    public static VariableTypeInfo forJavaClass(JavaClassInfo javaClassInfo) {
        return new VariableTypeInfo(null, null, null, javaClassInfo);
    }

    public Class<?> getArrayElementType() { return _arrayElementType; }
    public StructSymbol getStructType() { return _structType; }
    public StructSymbol getArrayElementStructType() { return _arrayElementStructType; }
    public JavaClassInfo getJavaClassType() { return _javaClassType; }
    public JavaResolvedType getJavaResolvedType() { return _javaResolvedType; }
}
