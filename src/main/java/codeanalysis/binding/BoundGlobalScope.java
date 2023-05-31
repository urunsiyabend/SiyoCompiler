package codeanalysis.binding;

import codeanalysis.DiagnosticBox;
import codeanalysis.VariableSymbol;

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
    private final Iterable<VariableSymbol> _variableSymbols;
    private final BoundStatement _boundStatement;

    /**
     * Initializes a new instance of the BoundGlobalScope class with the specified previous bound global scope, diagnostic box, variable symbols and bound expression.
     *
     * @param previous The previous bound global scope.
     * @param diagnostics The diagnostic box.
     * @param variableSymbols The variable symbols.
     * @param boundExpression The bound expression.
     */
    public BoundGlobalScope(BoundGlobalScope previous, DiagnosticBox diagnostics, Iterable<VariableSymbol> variableSymbols, BoundStatement statement) {
        _previous = previous;
        _diagnostics = diagnostics;
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
