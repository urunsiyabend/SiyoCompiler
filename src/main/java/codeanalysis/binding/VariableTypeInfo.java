package codeanalysis.binding;

import codeanalysis.StructSymbol;

/**
 * Tracks additional type metadata for variables beyond their Class<?> type.
 * Used to resolve array element types and struct field types.
 */
public class VariableTypeInfo {
    private final Class<?> _arrayElementType;
    private final StructSymbol _structType;
    private final StructSymbol _arrayElementStructType;

    public VariableTypeInfo(Class<?> arrayElementType, StructSymbol structType, StructSymbol arrayElementStructType) {
        _arrayElementType = arrayElementType;
        _structType = structType;
        _arrayElementStructType = arrayElementStructType;
    }

    public static VariableTypeInfo forArray(Class<?> elementType) {
        return new VariableTypeInfo(elementType, null, null);
    }

    public static VariableTypeInfo forArray(Class<?> elementType, StructSymbol elementStructType) {
        return new VariableTypeInfo(elementType, null, elementStructType);
    }

    public static VariableTypeInfo forStruct(StructSymbol structType) {
        return new VariableTypeInfo(null, structType, null);
    }

    public Class<?> getArrayElementType() { return _arrayElementType; }
    public StructSymbol getStructType() { return _structType; }
    public StructSymbol getArrayElementStructType() { return _arrayElementStructType; }
}
