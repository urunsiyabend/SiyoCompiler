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
