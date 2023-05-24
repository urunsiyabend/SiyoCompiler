package com.urunsiyabend.codeanalysis.binding;

import com.urunsiyabend.codeanalysis.VariableSymbol;

/**
 * Represents a bound assignment expression in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundAssignmentExpression extends BoundExpression {
    private final VariableSymbol _variable;
    private final BoundExpression _boundExpression;

    /**
     * Initializes a new instance of the BoundAssignmentExpression class.
     *
     * @param variable       The variable symbol being assigned to.
     * @param boundExpression The bound expression representing the value being assigned.
     */
    public BoundAssignmentExpression(VariableSymbol variable, BoundExpression boundExpression) {
        _variable = variable;
        _boundExpression = boundExpression;
    }

    /**
     * Gets the variable being assigned to.
     *
     * @return The variable symbol.
     */
    public VariableSymbol getVariable() {
        return _variable;
    }

    /**
     * Gets the expression being assigned.
     *
     * @return The bound expression.
     */
    public BoundExpression getExpression() {
        return _boundExpression;
    }

    /**
     * Gets the class type of the bound expression.
     *
     * @return The class type of the bound expression.
     */
    @Override
    public Class<?> getClassType() {
        return _boundExpression.getClassType();
    }

    /**
     * Gets the type of the bound expression.
     *
     * @return The type of the bound expression.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.AssignmentExpression;
    }
}
