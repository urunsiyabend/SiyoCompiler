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
            if (elem instanceof Byte b) elements.add(b & 0xFF);
            else if (elem instanceof Short s) elements.add((int) s);
            else if (elem instanceof Float f) elements.add((double) f);
            else if (elem instanceof Character c) elements.add(String.valueOf(c));
            else elements.add(elem);
        }
        return new SiyoArray(elements, Object.class);
    }

    /**
     * Convert a Siyo value into the Java array a Java method expects.
     *
     * <p>The compiler calls this at a Java call boundary whose parameter is an
     * array type. Without it a SiyoArray reference reached a method declared to
     * take {@code byte[]} and the class failed JVM verification before running:
     * every socket and file write in a Siyo program hit this.
     *
     * @param value The Siyo value — a SiyoArray, a List, or an already-Java array.
     * @param componentDescriptor JVM descriptor of the array's component type,
     *                            e.g. "B" for byte[] or "Ljava/lang/String;".
     * @return A Java array of the requested component type.
     */
    public static Object toJavaArray(Object value, String componentDescriptor) {
        if (value == null) return null;
        Class<?> component = componentTypeOf(componentDescriptor);
        if (component != null && value.getClass().isArray()
                && value.getClass().getComponentType() == component) {
            return value; // already the right shape
        }

        List<Object> elements;
        if (value instanceof SiyoArray siyoArray) elements = siyoArray.getElements();
        else if (value instanceof List<?> list) elements = new ArrayList<>(list);
        else if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) elements.add(java.lang.reflect.Array.get(value, i));
        } else {
            elements = List.of(value);
        }

        if (component == null) component = Object.class;
        Object result = java.lang.reflect.Array.newInstance(component, elements.size());
        for (int i = 0; i < elements.size(); i++) {
            java.lang.reflect.Array.set(result, i, coerceElement(elements.get(i), component));
        }
        return result;
    }

    private static Class<?> componentTypeOf(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) return Object.class;
        return switch (descriptor.charAt(0)) {
            case 'B' -> byte.class;
            case 'S' -> short.class;
            case 'I' -> int.class;
            case 'J' -> long.class;
            case 'F' -> float.class;
            case 'D' -> double.class;
            case 'Z' -> boolean.class;
            case 'C' -> char.class;
            case 'L' -> {
                String name = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                try {
                    yield Class.forName(name);
                } catch (ClassNotFoundException e) {
                    yield Object.class;
                }
            }
            case '[' -> {
                try {
                    yield Class.forName(descriptor.replace('/', '.'));
                } catch (ClassNotFoundException e) {
                    yield Object.class;
                }
            }
            default -> Object.class;
        };
    }

    /** Narrow a Siyo element to the Java component type the array needs. */
    private static Object coerceElement(Object element, Class<?> component) {
        if (element == null) return component.isPrimitive() ? zeroOf(component) : null;
        if (component == byte.class) return (byte) ((Number) element).intValue();
        if (component == short.class) return (short) ((Number) element).intValue();
        if (component == int.class) return ((Number) element).intValue();
        if (component == long.class) return ((Number) element).longValue();
        if (component == float.class) return ((Number) element).floatValue();
        if (component == double.class) return ((Number) element).doubleValue();
        if (component == boolean.class) return element instanceof Boolean b ? b : Boolean.parseBoolean(element.toString());
        if (component == char.class) {
            if (element instanceof Character c) return c;
            String text = element.toString();
            return text.isEmpty() ? (char) 0 : text.charAt(0);
        }
        return element;
    }

    private static Object zeroOf(Class<?> primitive) {
        if (primitive == boolean.class) return false;
        if (primitive == char.class) return (char) 0;
        if (primitive == long.class) return 0L;
        if (primitive == float.class) return 0.0f;
        if (primitive == double.class) return 0.0d;
        return 0;
    }
}
