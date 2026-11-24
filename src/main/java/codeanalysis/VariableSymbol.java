package codeanalysis;

/**
 * The VariableSymbol class represents a variable symbol.
 * It encapsulates name and type of variable.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class VariableSymbol {
    private final String _name;
    private final boolean _isReadOnly;
    private final Class<?> _type;
    private String _ownerClass;
    private String _fieldName;
    private String _declaredTypeName;
    private boolean _isCell;

    /**
     * Creates a new instance of the VariableSymbol class with the specified name and type.
     *
     * @param name The name of variable
     * @param type The type of variable
     */
    public VariableSymbol(String name, boolean isReadOnly, Class<?> type) {
        _name = name;
        _isReadOnly = isReadOnly;
        _type = type;
    }

    /**
     * The JVM class that owns this variable as a static field, or null when it
     * belongs to the class currently being emitted.
     *
     * <p>Set for a module-level variable reached through an import, so that
     * {@code status.OK} reads the field on the module's own class instead of
     * looking for a field that the importing class never declared.
     *
     * @return The owning class name, or null.
     */
    public String getOwnerClass() {
        return _ownerClass;
    }

    /**
     * The field name to use on the owning class. Differs from {@link #getName()}
     * for an imported variable, whose Siyo name is qualified ("status.OK") while
     * its field name is not ("OK").
     *
     * @return The field name.
     */
    public String getFieldName() {
        return _fieldName != null ? _fieldName : _name;
    }

    /**
     * Whether this variable is held in a one-element cell rather than directly
     * in its slot.
     *
     * <p>A closure captures by value, so a write inside one could not be seen
     * outside it. A local that a closure both captures and writes is stored in
     * a cell instead, and the closure captures the cell, which is what makes
     * the write shared.</p>
     *
     * @return True when the variable is held in a cell.
     */
    public boolean isCell() {
        return _isCell;
    }

    public void setCell(boolean isCell) {
        _isCell = isCell;
    }

    /**
     * The type name this variable was declared with, when one was written.
     *
     * <p>The Java class alone loses what a declaration said: every function
     * type is {@code SiyoClosure}, so {@code fn(int) -> int} and
     * {@code fn(string)} are indistinguishable without the name.</p>
     *
     * @return The declared type name, or null.
     */
    private boolean _structDerived;
    private java.util.Map<String, StructSymbol> _originStructs;

    /**
     * For a variable imported from another module, the structs its exporter
     * could name, so its type name is read in the exporter's terms rather than
     * by whatever those names mean where it is used. Null for a local variable.
     */
    public java.util.Map<String, StructSymbol> getOriginStructs() {
        return _originStructs;
    }

    public void setOriginStructs(java.util.Map<String, StructSymbol> originStructs) {
        _originStructs = originStructs;
    }

    private long _originVersion = -1;
    private boolean _inferredType;
    /**
     * The provenance of the storage a variable's value may share with others'.
     * Variables holding the same object (an alias, an element or field taken
     * from one) share one cell. A value held inside another (in a literal, a
     * field, a pushed element) feeds the container's cell, and never the
     * reverse: what reaches the value is seen through the container, but a
     * fresh value stored into a container does not take on what the container
     * holds. Roots only grow, and each reaches a cell at most once.
     */
    private static final class ProvenanceCell {
        private ProvenanceCell _parent;
        private final java.util.Set<Object> _roots = new java.util.LinkedHashSet<>();
        // Cells holding this one's object, and cells whose objects this one holds.
        private final java.util.Set<ProvenanceCell> _containers =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private final java.util.Set<ProvenanceCell> _contents =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        // Containers this cell's object was taken out of (an element, a field, a
        // loop item): a store into it is a store into what they hold.
        private final java.util.Set<ProvenanceCell> _elementOf =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        ProvenanceCell find() {
            ProvenanceCell cell = this;
            while (cell._parent != null) cell = cell._parent;
            ProvenanceCell step = this;
            while (step != cell) {
                ProvenanceCell next = step._parent;
                step._parent = cell;
                step = next;
            }
            return cell;
        }

        /** Adds roots here and to every cell holding this one, transitively. */
        static void flow(ProvenanceCell start, java.util.Collection<Object> roots) {
            java.util.ArrayDeque<ProvenanceCell> cells = new java.util.ArrayDeque<>();
            java.util.ArrayDeque<java.util.List<Object>> carried = new java.util.ArrayDeque<>();
            cells.add(start.find());
            carried.add(new java.util.ArrayList<>(roots));
            while (!cells.isEmpty()) {
                ProvenanceCell cell = cells.pop().find();
                java.util.List<Object> added = new java.util.ArrayList<>();
                for (Object root : carried.pop()) {
                    if (cell._roots.add(root)) added.add(root);
                }
                if (added.isEmpty()) continue;
                for (ProvenanceCell container : cell._containers) {
                    cells.add(container);
                    carried.add(added);
                }
            }
        }
    }

    private final ProvenanceCell _cell = new ProvenanceCell();

    /**
     * What this variable's value, and anything stored into it or into storage it
     * shares, was inferred from by 0.10.0: imported values, imported calls named
     * from their declaration, and generic results (see TypeResolver.provenanceOf).
     */
    public java.util.Set<Object> getProvenance() {
        return java.util.Collections.unmodifiableSet(_cell.find()._roots);
    }

    public void addProvenance(java.util.Set<Object> roots) {
        if (roots.isEmpty()) return;
        ProvenanceCell.flow(_cell, roots);
        for (ProvenanceCell container : new java.util.ArrayList<>(_cell.find()._elementOf)) {
            intoContents(container, roots);
        }
    }

    /**
     * Adds roots to what this variable holds deep inside: a store into a field
     * or element of an object it holds, which may be any object stored into it.
     */
    public void addProvenanceToContents(java.util.Set<Object> roots) {
        if (roots.isEmpty()) return;
        intoContents(_cell, roots);
        for (ProvenanceCell container : new java.util.ArrayList<>(_cell.find()._elementOf)) {
            intoContents(container, roots);
        }
    }

    private static void intoContents(ProvenanceCell start, java.util.Set<Object> roots) {
        java.util.Set<ProvenanceCell> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<ProvenanceCell> pending = new java.util.ArrayDeque<>();
        pending.add(start.find());
        while (!pending.isEmpty()) {
            ProvenanceCell cell = pending.pop().find();
            if (!seen.add(cell)) continue;
            ProvenanceCell.flow(cell, roots);
            pending.addAll(cell._contents);
        }
    }

    /**
     * Records that this variable's object was taken out of {@code container}'s
     * (an element, a field, a loop item, a call's result): it sees whatever the
     * container does, and a store into it is a store into the container's contents.
     */
    public void elementOf(VariableSymbol container) {
        container.heldIn(this);
        ProvenanceCell mine = _cell.find();
        ProvenanceCell theirs = container._cell.find();
        if (mine != theirs) mine._elementOf.add(theirs);
    }

    /** Records that this variable holds the same object as {@code other}: one provenance for both. */
    public void shareStorageWith(VariableSymbol other) {
        ProvenanceCell mine = _cell.find();
        ProvenanceCell theirs = other._cell.find();
        if (mine == theirs) return;
        java.util.List<Object> theirRoots = new java.util.ArrayList<>(theirs._roots);
        java.util.List<Object> myRoots = new java.util.ArrayList<>(mine._roots);
        java.util.List<ProvenanceCell> theirContainers = new java.util.ArrayList<>(theirs._containers);
        theirs._parent = mine;
        mine._containers.addAll(theirs._containers);
        mine._contents.addAll(theirs._contents);
        mine._elementOf.addAll(theirs._elementOf);
        mine._containers.remove(mine);
        mine._contents.remove(mine);
        mine._elementOf.remove(mine);
        ProvenanceCell.flow(mine, theirRoots);
        for (ProvenanceCell container : theirContainers) ProvenanceCell.flow(container, myRoots);
    }

    /** Records that this variable's object is held inside {@code container}'s: what reaches it reaches that. */
    public void heldIn(VariableSymbol container) {
        ProvenanceCell held = _cell.find();
        ProvenanceCell holder = container._cell.find();
        if (held == holder) return;
        held._containers.add(holder);
        holder._contents.add(held);
        ProvenanceCell.flow(holder, new java.util.ArrayList<>(held._roots));
    }

    /** Whether a local's type name was inferred by 0.10.0 rather than written in an annotation. */
    public boolean isInferredType() { return _inferredType; }

    public void setInferredType(boolean inferred) { _inferredType = inferred; }

    /** The generation of this file's tables a local variable's types were captured in; -1 for an exporter's. */
    public long getOriginVersion() { return _originVersion; }

    public void setOriginVersion(long version) { _originVersion = version; }

    private java.util.Map<String, UnionSymbol> _originUnions = java.util.Map.of();
    private java.util.Map<String, InterfaceSymbol> _originInterfaces = java.util.Map.of();

    /** The sum types and interfaces the exporter could name, beside its structs. */
    public java.util.Map<String, UnionSymbol> getOriginUnions() { return _originUnions; }

    public java.util.Map<String, InterfaceSymbol> getOriginInterfaces() { return _originInterfaces; }

    public void setOriginTypes(java.util.Map<String, UnionSymbol> unions, java.util.Map<String, InterfaceSymbol> interfaces) {
        _originUnions = unions == null ? java.util.Map.of() : unions;
        _originInterfaces = interfaces == null ? java.util.Map.of() : interfaces;
    }

    /**
     * Whether this variable's type came from a generic struct's type argument,
     * so its whole declared type name is what it holds. It travels with the
     * symbol, into an importing module too.
     */
    public boolean isStructDerived() {
        return _structDerived;
    }

    public void setStructDerived(boolean structDerived) {
        _structDerived = structDerived;
    }

    public String getDeclaredTypeName() {
        return _declaredTypeName;
    }

    public void setDeclaredTypeName(String declaredTypeName) {
        _declaredTypeName = declaredTypeName;
    }

    public void setOwner(String ownerClass, String fieldName) {
        _ownerClass = ownerClass;
        _fieldName = fieldName;
    }

    /**
     * Retrieves the name of the variable.
     *
     * @return The name of the variable.
     */
    public String getName() {
        return _name;
    }

    /**
     * Retrieves whether the variable is read-only or not.
     *
     * @return Whether the variable is read-only or not.
     */
    public boolean isReadOnly() {
        return _isReadOnly;
    }

    /**
     * Retrieves the type of the variable.
     *
     * @return The type of the variable.
     */
    public Class<?> getType() {
        return _type;
    }

    /**
     * Returns a string representation of the variable symbol.
     * The string representation consists of the name of the variable.
     *
     * @return A string representation of the variable symbol.
     */
    @Override
    public String toString() {
        return _name;
    }
}
