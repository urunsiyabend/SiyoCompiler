package codeanalysis;

import java.util.List;

/**
 * The FunctionSymbol class represents a function symbol.
 * It encapsulates the function name, parameters, and return type.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class FunctionSymbol {
    private final String _name;
    private final List<ParameterSymbol> _parameters;
    private final Class<?> _returnType;

    /**
     * Creates a new instance of the FunctionSymbol class.
     *
     * @param name       The name of the function.
     * @param parameters The list of parameters.
     * @param returnType The return type of the function (null for void).
     */
    public FunctionSymbol(String name, List<ParameterSymbol> parameters, Class<?> returnType) {
        _name = name;
        _parameters = parameters;
        _returnType = returnType;
    }

    /**
     * Gets the name of the function.
     *
     * @return The function name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Gets the list of parameters.
     *
     * @return The parameters.
     */
    public List<ParameterSymbol> getParameters() {
        return _parameters;
    }

    /**
     * Gets the return type of the function.
     *
     * @return The return type, or null for void functions.
     */
    public Class<?> getReturnType() {
        return _returnType;
    }

    /**
     * Returns a string representation of the function symbol.
     *
     * @return The function name.
     */
    @Override
    public String toString() {
        return _name;
    }
}
