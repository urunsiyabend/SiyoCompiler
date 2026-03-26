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

    public StructSymbol(String name, LinkedHashMap<String, Class<?>> fields) {
        _name = name;
        _fields = fields;
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
