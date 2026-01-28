package codeanalysis.binding;

import codeanalysis.FunctionSymbol;
import codeanalysis.VariableSymbol;

import java.util.HashMap;
import java.util.Map;

/**
 * The BoundScope class represents a scope in the code analysis process.
 * It encapsulates a map of variables and provides methods for declaring and looking up variables.
 * It also provides a method for getting all the variables declared in the scope.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
*/
public class BoundScope {
    private final Map<String, VariableSymbol> _variables = new HashMap<>();
    private final Map<String, FunctionSymbol> _functions = new HashMap<>();
    private final BoundScope _parent;

    /**
     * Constructs a BoundScope object with the specified parent scope.
     *
     * @param parent The parent scope of the scope to be constructed.
     */
    public BoundScope(BoundScope parent) {
        _parent = parent;
    }

    /**
     * Tries to declare the specified variable in the scope.
     * If the variable is already declared in the scope, returns false.
     *
     * @param variableSymbol The variable to be declared.
     */
    public boolean tryDeclare(VariableSymbol variableSymbol) {
        if (_variables.containsKey(variableSymbol.getName())) {
            return false;
        }

        _variables.put(variableSymbol.getName(), variableSymbol);
        return true;
    }

    /**
     * Tries to look up the specified variable in the scope.
     * If the variable is not declared in the scope, returns false.
     *
     * @param name The name of the variable to be looked up.
     * @return True if the variable is declared in the scope, false otherwise.
     */
    public boolean tryLookup(String name) {
        if (_variables.containsKey(name)) {
            return true;
        }
        if (_parent == null) {
            return false;
        }
        return _parent.tryLookup(name);
    }

    /**
     * Looks up the specified variable in the scope.
     * If the variable is not declared in the scope, returns null.
     *
     * @param name The name of the variable to be looked up.
     * @return The variable if it is declared in the scope, null otherwise.
     */
    public VariableSymbol lookupVariable(String name) {
        VariableSymbol variable = _variables.get(name);
        if (variable != null) {
            return variable;
        }
        if (_parent == null) {
            return null;
        }
        return _parent.lookupVariable(name);
    }

    /**
     * Gets all the variables declared in the scope.
     *
     * @return An iterable of all the variables declared in the scope.
     */
    public Iterable<VariableSymbol> getDeclaredVariables() {
        return _variables.values();
    }

    /**
     * Gets the parent scope of the scope.
     *
     * @return The parent scope of the scope.
     */
    public BoundScope getParent() {
        return _parent;
    }

    /**
     * Tries to declare the specified function in the scope.
     * If the function is already declared in the scope, returns false.
     *
     * @param functionSymbol The function to be declared.
     * @return True if the function was declared, false if it already exists.
     */
    public boolean tryDeclareFunction(FunctionSymbol functionSymbol) {
        if (_functions.containsKey(functionSymbol.getName())) {
            return false;
        }

        _functions.put(functionSymbol.getName(), functionSymbol);
        return true;
    }

    /**
     * Tries to look up the specified function in the scope.
     * If the function is not declared in the scope, returns false.
     *
     * @param name The name of the function to be looked up.
     * @return True if the function is declared in the scope, false otherwise.
     */
    public boolean tryLookupFunction(String name) {
        if (_functions.containsKey(name)) {
            return true;
        }
        if (_parent == null) {
            return false;
        }
        return _parent.tryLookupFunction(name);
    }

    /**
     * Looks up the specified function in the scope.
     * If the function is not declared in the scope, returns null.
     *
     * @param name The name of the function to be looked up.
     * @return The function if it is declared in the scope, null otherwise.
     */
    public FunctionSymbol lookupFunction(String name) {
        FunctionSymbol function = _functions.get(name);
        if (function != null) {
            return function;
        }
        if (_parent == null) {
            return null;
        }
        return _parent.lookupFunction(name);
    }

    /**
     * Gets all the functions declared in the scope.
     *
     * @return An iterable of all the functions declared in the scope.
     */
    public Iterable<FunctionSymbol> getDeclaredFunctions() {
        return _functions.values();
    }
}
