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
