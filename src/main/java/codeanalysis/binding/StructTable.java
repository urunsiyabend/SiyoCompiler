package codeanalysis.binding;

import codeanalysis.StructSymbol;


/**
 * The structs a file can name, found by the name a type is written with.
 *
 * <p>A generic struct is declared once and erased, so {@code Group<Row>} and
 * {@code Group<int>} both find the declaration of {@code Group}. Type names
 * are carried as written throughout the binder, and every place that looks a
 * struct up by one would otherwise have to remember to drop the arguments.
 */
final class StructTable extends VersionedMap<String, StructSymbol> {
    StructTable(VersionedMap.Version version) {
        super(version);
    }

    @Override
    public StructSymbol get(Object key) {
        StructSymbol found = super.get(key);
        if (found != null || !(key instanceof String name)) return found;
        String base = TypeResolver.genericBaseName(name);
        return base != null ? super.get(base) : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }
}
