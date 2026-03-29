package codeanalysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in Map type for Siyo. String keys, any values.
 */
public class SiyoMap {
    private final LinkedHashMap<String, Object> _entries = new LinkedHashMap<>();

    public void set(String key, Object value) { _entries.put(key, value); }
    public Object get(String key) { return _entries.getOrDefault(key, null); }
    public boolean has(String key) { return _entries.containsKey(key); }
    public void remove(String key) { _entries.remove(key); }
    public int size() { return _entries.size(); }

    public SiyoArray keys() {
        return new SiyoArray(new ArrayList<>(_entries.keySet()), String.class);
    }

    public SiyoArray values() {
        return new SiyoArray(new ArrayList<>(_entries.values()), Object.class);
    }

    /** Increment integer value by 1. If key doesn't exist, set to 1. */
    public void increment(String key) {
        Object val = _entries.get(key);
        if (val instanceof Integer i) {
            _entries.put(key, i + 1);
        } else {
            _entries.put(key, 1);
        }
    }

    /** Get integer value, default 0. */
    public int getInt(String key) {
        Object val = _entries.get(key);
        if (val instanceof Integer i) return i;
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : _entries.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
