package codeanalysis;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an array value in the Siyo language runtime.
 * Implements List<Object> for JVM interop compatibility.
 */
public class SiyoArray extends AbstractList<Object> implements List<Object> {
    private final ArrayList<Object> _elements;
    private final Class<?> _elementType;

    public SiyoArray(List<Object> elements, Class<?> elementType) {
        _elements = new ArrayList<>(elements);
        _elementType = elementType;
    }

    @Override
    public Object get(int index) {
        return _elements.get(index);
    }

    @Override
    public Object set(int index, Object value) {
        return _elements.set(index, value);
    }

    @Override
    public int size() {
        return _elements.size();
    }

    @Override
    public void add(int index, Object element) {
        _elements.add(index, element);
    }

    @Override
    public Object remove(int index) {
        return _elements.remove(index);
    }

    public int length() {
        return _elements.size();
    }

    public boolean add(Object value) {
        return _elements.add(value);
    }

    public Class<?> getElementType() {
        return _elementType;
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

    /**
     * Convert a Java array (Object[] or primitive[]) to a SiyoArray.
     * Used by the bytecode emitter to wrap Java interop array returns.
     */
    public static SiyoArray fromJavaArray(Object javaArray) {
        if (javaArray == null) return new SiyoArray(List.of(), Object.class);
        if (javaArray instanceof Object[] arr) {
            return new SiyoArray(java.util.Arrays.asList(arr), Object.class);
        }
        int len = java.lang.reflect.Array.getLength(javaArray);
        ArrayList<Object> elements = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            Object elem = java.lang.reflect.Array.get(javaArray, i);
            if (elem instanceof Byte b) elements.add((int) b);
            else if (elem instanceof Short s) elements.add((int) s);
            else if (elem instanceof Float f) elements.add((double) f);
            else if (elem instanceof Character c) elements.add(String.valueOf(c));
            else elements.add(elem);
        }
        return new SiyoArray(elements, Object.class);
    }
}
