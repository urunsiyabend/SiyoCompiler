package codeanalysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Built-in Map type for Siyo. Any keys, any values.
 */
public class SiyoMap {
    private final LinkedHashMap<Object, Object> _entries = new LinkedHashMap<>();

    // Object-keyed methods (primary API)
    public void set(Object key, Object value) { _entries.put(key, value); }
    public Object get(Object key) { return _entries.getOrDefault(key, null); }
    public boolean has(Object key) { return _entries.containsKey(key); }
    public void remove(Object key) { _entries.remove(key); }
    public int size() { return _entries.size(); }

    // String-keyed overloads for backward compatibility
    public void set(String key, Object value) { _entries.put(key, value); }
    public Object get(String key) { return _entries.getOrDefault(key, null); }
    public boolean has(String key) { return _entries.containsKey(key); }
    public void remove(String key) { _entries.remove(key); }

    public SiyoArray keys() {
        return new SiyoArray(new ArrayList<>(_entries.keySet()), Object.class);
    }

    public SiyoArray values() {
        return new SiyoArray(new ArrayList<>(_entries.values()), Object.class);
    }

    /** Increment integer value by 1. If key doesn't exist, set to 1. */
    public void increment(Object key) {
        Object val = _entries.get(key);
        if (val instanceof Integer i) {
            _entries.put(key, i + 1);
        } else {
            _entries.put(key, 1);
        }
    }

    /** Get integer value, default 0. */
    public int getInt(Object key) {
        Object val = _entries.get(key);
        if (val instanceof Integer i) return i;
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
        return 0;
    }

    /** Get long value, default 0. */
    public long getLong(Object key) {
        Object val = _entries.get(key);
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return (long) i;
        return 0L;
    }

    /** Get string value, default "". */
    public String getStr(Object key) {
        Object val = _entries.get(key);
        if (val == null) return "";
        return val.toString();
    }

    /** Get boolean value, default false. */
    public boolean getBool(Object key) {
        Object val = _entries.get(key);
        if (val instanceof Boolean b) return b;
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Object, Object> e : _entries.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
