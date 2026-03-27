package codeanalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an array value in the Siyo language runtime.
 */
public class SiyoArray {
    private final List<Object> _elements;
    private final Class<?> _elementType;

    public SiyoArray(List<Object> elements, Class<?> elementType) {
        _elements = new ArrayList<>(elements);
        _elementType = elementType;
    }

    public Object get(int index) {
        return _elements.get(index);
    }

    public void set(int index, Object value) {
        _elements.set(index, value);
    }

    public int length() {
        return _elements.size();
    }

    public Class<?> getElementType() {
        return _elementType;
    }

    public void add(Object value) {
        _elements.add(value);
    }

    public Object remove(int index) {
        return _elements.remove(index);
    }

    public List<Object> getElements() {
        return _elements;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < _elements.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(_elements.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SiyoArray other)) return false;
        return _elements.equals(other._elements);
    }

    @Override
    public int hashCode() {
        return _elements.hashCode();
    }
}
