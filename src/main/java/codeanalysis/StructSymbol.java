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

    public StructSymbol(String name, LinkedHashMap<String, Class<?>> fields) {
        this(name, fields, new LinkedHashMap<>());
    }

    public StructSymbol(String name, LinkedHashMap<String, Class<?>> fields, LinkedHashMap<String, String> fieldTypeNames) {
        _name = name;
        _fields = fields;
        _fieldTypeNames = fieldTypeNames;
    }

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
