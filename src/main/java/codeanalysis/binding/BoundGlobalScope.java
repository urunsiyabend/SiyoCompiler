package codeanalysis.binding;

import codeanalysis.DiagnosticBox;
import codeanalysis.FunctionSymbol;
import codeanalysis.VariableSymbol;

import java.util.Map;

/**
 * The `BoundGlobalScope` class is responsible for binding global scope syntax to bound global scope.
 * It performs type checking and generates diagnostic messages for any binding errors.
 * It also holds the previous bound global scope, the diagnostic box, the variable symbols and the bound expression.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundGlobalScope {
    private final BoundGlobalScope _previous;
    private final DiagnosticBox _diagnostics;
    private final Iterable<FunctionSymbol> _functionSymbols;
    private final Map<FunctionSymbol, BoundBlockStatement> _functionBodies;
    private final Iterable<VariableSymbol> _variableSymbols;
    private final BoundStatement _boundStatement;

    /**
     * Initializes a new instance of the BoundGlobalScope class with the specified previous bound global scope, diagnostic box, variable symbols and bound expression.
     *
     * @param previous The previous bound global scope.
     * @param diagnostics The diagnostic box.
     * @param functionSymbols The function symbols.
     * @param functionBodies The map of function symbols to their bound bodies.
     * @param variableSymbols The variable symbols.
     * @param statement The bound statement.
     */
    public BoundGlobalScope(BoundGlobalScope previous, DiagnosticBox diagnostics, Iterable<FunctionSymbol> functionSymbols, Map<FunctionSymbol, BoundBlockStatement> functionBodies, Iterable<VariableSymbol> variableSymbols, BoundStatement statement) {
        _previous = previous;
        _diagnostics = diagnostics;
        _functionSymbols = functionSymbols;
        _functionBodies = functionBodies;
        _variableSymbols = variableSymbols;
        _boundStatement = statement;
    }

    /**
     * Gets the diagnostic box.
     *
     * @return The diagnostic box of the bound global scope.
     */
    public DiagnosticBox getDiagnostics() {
        return _diagnostics;
    }

    /**
     * Gets the function symbols.
     *
     * @return The function symbols of the bound global scope.
     */
    public Iterable<FunctionSymbol> getFunctionSymbols() {
        return _functionSymbols;
    }

    /**
     * Gets the function bodies map.
     *
     * @return The map of function symbols to their bound bodies.
     */
    public Map<FunctionSymbol, BoundBlockStatement> getFunctionBodies() {
        return _functionBodies;
    }

    /**
     * Gets the variable symbols.
     *
     * @return The variable symbols of the bound global scope.
     */
    public Iterable<VariableSymbol> getVariableSymbols() {
        return _variableSymbols;
    }

    /**
     * Gets the bound statement.
     *
     * @return The bound statement of the bound global scope.
     */
    public BoundStatement getBoundStatement() {
        return _boundStatement;
    }

    /**
     * Gets the previous bound global scope.
     *
     * @return The previous bound global scope of the bound global scope.
     */
    public BoundGlobalScope getPrevious() {
        return _previous;
    }

}
