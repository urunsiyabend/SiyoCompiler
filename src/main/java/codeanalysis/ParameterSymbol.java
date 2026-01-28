package codeanalysis;

/**
 * The ParameterSymbol class represents a function parameter symbol.
 * It encapsulates the parameter name and type.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ParameterSymbol extends VariableSymbol {

    /**
     * Creates a new instance of the ParameterSymbol class.
     *
     * @param name The name of the parameter.
     * @param type The type of the parameter.
     */
    public ParameterSymbol(String name, Class<?> type) {
        super(name, true, type);  // Parameters are read-only
    }
}
