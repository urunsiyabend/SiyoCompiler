package codeanalysis.binding;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A type table that remembers every change. The struct, sum type and interface
 * tables of one file share a {@link Version}, which moves on each change to any
 * of them, so a name captured into a type can be known still to mean what it
 * meant, whichever import, at the top of the file or inside a function, changed
 * a table since; and {@link #asOf} reads what it meant then without a copy.
 *
 * <p>A table changes only through {@code put}, {@code putIfAbsent} and
 * {@code remove}, each recorded. Every other way to change it, including
 * through its key, value and entry views, is refused, so no change goes unseen.
 */
class VersionedMap<K, V> extends LinkedHashMap<K, V> {
    /** The generation the tables of one file are at, and the name each change was to. */
    static final class Version {
        private final List<Object> _changed = new ArrayList<>();

        long value() {
            return _changed.size();
        }

        void bump(Object key) {
            _changed.add(key);
        }

        /** The names changed in any of the tables since {@code version}, in order. */
        List<Object> changedSince(long version) {
            return _changed.subList((int) version, _changed.size());
        }
    }

    private record Change<V>(long version, V value) {
    }

    private final Version _version;
    private final Map<Object, List<Change<V>>> _history = new HashMap<>();

    VersionedMap(Version version) {
        _version = version;
    }

    /** This table as it stood at a generation, read-only. */
    Map<K, V> asOf(long version) {
        return new AbstractMap<>() {
            @Override
            public V get(Object key) {
                List<Change<V>> changes = _history.get(key);
                if (changes == null) return null;
                for (int i = changes.size() - 1; i >= 0; i--) {
                    if (changes.get(i).version() <= version) return changes.get(i).value();
                }
                return null;
            }

            @Override
            public boolean containsKey(Object key) {
                return get(key) != null;
            }

            @Override
            public Set<Entry<K, V>> entrySet() {
                throw new UnsupportedOperationException("a past type table is read by name");
            }
        };
    }

    /** Every value this table has held under a key, oldest first. */
    List<V> everHeld(Object key) {
        List<V> values = new ArrayList<>();
        for (Change<V> change : _history.getOrDefault(key, List.of())) {
            if (change.value() != null) values.add(change.value());
        }
        return values;
    }

    private void record(Object key, V value) {
        _version.bump(key);
        _history.computeIfAbsent(key, k -> new ArrayList<>()).add(new Change<>(_version.value(), value));
    }

    @Override
    public V put(K key, V value) {
        if (!super.containsKey(key) || super.get(key) != value) record(key, value);
        return super.put(key, value);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V current = super.get(key);
        if (current != null) return current;
        return put(key, value);
    }

    @Override
    public V remove(Object key) {
        if (super.containsKey(key)) record(key, null);
        return super.remove(key);
    }

    @Override
    public Set<K> keySet() {
        return Collections.unmodifiableSet(super.keySet());
    }

    @Override
    public Collection<V> values() {
        return Collections.unmodifiableCollection(super.values());
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return Collections.unmodifiableSet(super.entrySet());
    }

    private static UnsupportedOperationException unrecorded() {
        return new UnsupportedOperationException("a type table changes only by put, putIfAbsent or remove");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw unrecorded();
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw unrecorded();
    }

    @Override
    public void clear() {
        throw unrecorded();
    }

    @Override
    public V replace(K key, V value) {
        throw unrecorded();
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        throw unrecorded();
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw unrecorded();
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
        throw unrecorded();
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
        throw unrecorded();
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mapping) {
        throw unrecorded();
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remapping) {
        throw unrecorded();
    }
}
