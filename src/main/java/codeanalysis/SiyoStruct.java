package codeanalysis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a struct instance value in the Siyo language runtime.
 */
public class SiyoStruct {
    private final StructSymbol _type;
    private final Map<String, Object> _fields;

    public SiyoStruct(StructSymbol type, Map<String, Object> fields) {
        _type = type;
        _fields = new LinkedHashMap<>(fields);
    }

    public StructSymbol getStructType() {
        return _type;
    }

    public Object getField(String name) {
        return _fields.get(name);
    }

    public void setField(String name, Object value) {
        _fields.put(name, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(_type.getName());
        sb.append(" { ");
        boolean first = true;
        for (Map.Entry<String, Object> entry : _fields.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
            first = false;
        }
        sb.append(" }");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SiyoStruct other)) return false;
        return _type.getName().equals(other._type.getName()) && _fields.equals(other._fields);
    }

    @Override
    public int hashCode() {
        return _fields.hashCode();
    }
}
