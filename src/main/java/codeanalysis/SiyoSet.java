package codeanalysis;

import java.util.LinkedHashSet;
import java.util.ArrayList;

/**
 * Built-in Set type for Siyo. Any-type elements, ordered.
 */
public class SiyoSet {
    private final LinkedHashSet<Object> _elements = new LinkedHashSet<>();

    public void add(Object value) { _elements.add(value); }
    public boolean has(Object value) { return _elements.contains(value); }
    public void remove(Object value) { _elements.remove(value); }
    public int size() { return _elements.size(); }

    // String-keyed overloads for backward compat
    public void add(String value) { _elements.add(value); }
    public boolean has(String value) { return _elements.contains(value); }
    public void remove(String value) { _elements.remove(value); }

    public SiyoArray values() {
        return new SiyoArray(new ArrayList<>(_elements), Object.class);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Object e : _elements) {
            if (!first) sb.append(", ");
            sb.append(e);
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
