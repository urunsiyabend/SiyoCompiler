package com.urunsiyabend.codeanalysis.binding;

import com.urunsiyabend.codeanalysis.VariableSymbol;

/**
 * Represents a bound variable expression in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundVariableExpression extends BoundExpression {
    private final VariableSymbol _variable;

    /**
     * Creates a new instance of the BoundVariableExpression class.
     *
     * @param variable The variable symbol represented by the expression.
     */
    public BoundVariableExpression(VariableSymbol variable) {
        _variable = variable;
    }

    /**
     * Gets the variable symbol represented by the expression.
     *
     * @return The variable symbol.
     */
    public VariableSymbol getVariable() {
        return _variable;
    }

    /**
     * Gets the class type of the bound expression.
     *
     * @return The class type of the bound expression.
     */
    @Override
    public Class<?> getClassType() {
        return _variable.getType();
    }

    /**
     * Gets the type of the bound expression.
     *
     * @return The type of the bound expression.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.VariableExpression;
    }
}
