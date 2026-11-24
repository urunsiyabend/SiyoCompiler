package codeanalysis;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Represents a struct type definition in the Siyo language.
 */
public class StructSymbol {
    private final String _name;
    private final LinkedHashMap<String, Class<?>> _fields;
    private final LinkedHashMap<String, String> _fieldTypeNames;
    private boolean _isActor = false;
    private List<String> _typeParameters = List.of();
    private String _originModule;

    /** The file that declared this struct, which its own methods come from. */
    public String getOriginModule() { return _originModule; }

    public void setOriginModule(String module) { _originModule = module; }

    public StructSymbol(String name, LinkedHashMap<String, Class<?>> fields) {
        this(name, fields, new LinkedHashMap<>());
    }

    public StructSymbol(String name, LinkedHashMap<String, Class<?>> fields, LinkedHashMap<String, String> fieldTypeNames) {
        _name = name;
        _fields = fields;
        _fieldTypeNames = fieldTypeNames;
    }

    public boolean isActor() { return _isActor; }

    /**
     * The type parameters the struct declares: the {@code T} of
     * {@code struct Group<T> { items: Array<T> }}. They are erased at run time;
     * a value's type arguments travel with its declared type name.
     *
     * @return The type parameter names, empty when it declares none.
     */
    public List<String> getTypeParameters() { return _typeParameters; }

    public void setTypeParameters(List<String> typeParameters) {
        _typeParameters = typeParameters == null ? List.of() : List.copyOf(typeParameters);
    }
    public void setActor(boolean isActor) { _isActor = isActor; }

    public String getFieldTypeName(String fieldName) {
        return _fieldTypeNames.get(fieldName);
    }

    public String getName() {
        return _name;
    }

    public LinkedHashMap<String, Class<?>> getFields() {
        return _fields;
    }

    public Class<?> getFieldType(String fieldName) {
        return _fields.get(fieldName);
    }

    public boolean hasField(String fieldName) {
        return _fields.containsKey(fieldName);
    }

    public List<String> getFieldNames() {
        return List.copyOf(_fields.keySet());
    }

    @Override
    public String toString() {
        return _name;
    }
}
