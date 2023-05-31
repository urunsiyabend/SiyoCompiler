package codeanalysis.binding;

import codeanalysis.VariableSymbol;

/**
 * The `BoundVariableDeclaration` class is responsible for binding variable declaration syntax to bound variable declaration.
 * It performs type checking and generates diagnostic messages for any binding errors.
 * It also holds the variable symbol and the bound expression.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundVariableDeclaration extends BoundStatement {
    private final VariableSymbol _variable;
    private final BoundExpression _initializer;

    /**
     * Initializes a new instance of the BoundVariableDeclaration class with the specified variable symbol and bound expression.
     *
     * @param variable The variable symbol.
     * @param initializer The bound expression.
     */
    public BoundVariableDeclaration(VariableSymbol variable, BoundExpression initializer) {
        _variable = variable;
        _initializer = initializer;
    }

    /**
     * Gets the variable symbol.
     *
     * @return The variable symbol of the bound variable declaration.
     */
    public VariableSymbol getVariable() {
        return _variable;
    }

    /**
     * Gets the bound expression.
     *
     * @return The bound expression of the bound variable declaration.
     */
    public BoundExpression getInitializer() {
        return _initializer;
    }


    /**
     * Gets the type of the bound variable declaration.
     *
     * @return The type of the bound variable declaration.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.VariableDeclaration;
    }
}
