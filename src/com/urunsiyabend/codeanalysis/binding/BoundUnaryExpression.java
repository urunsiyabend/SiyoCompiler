package com.urunsiyabend.codeanalysis.binding;

/**
 * Represents a bound unary expression in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundUnaryExpression extends BoundExpression {
    private BoundUnaryOperator _operator;
    private BoundExpression _operand;

    /**
     * Constructs a new instance of the BoundUnaryExpression class.
     *
     * @param operatorType The operator type of the unary expression.
     * @param operand      The operand of the unary expression.
     */
    public BoundUnaryExpression(BoundUnaryOperator operator, BoundExpression operand) {
        _operator = operator;
        _operand = operand;
    }

    /**
     * Gets the type of the bound expression.
     *
     * @return The type of the bound expression.
     */
    @Override
    public BoundNodeType getType() {
        return BoundNodeType.UnaryExpression;
    }

    /**
     * Gets the class type of the bound expression.
     *
     * @return The class type of the bound expression.
     */
    @Override
    public Class<?> getClassType() {
        return getOperator().getResultType();
    }

    /**
     * Gets the operand of the unary expression.
     *
     * @return The operand of the unary expression.
     */
    public BoundExpression getOperand() {
        return _operand;
    }

    /**
     * Sets the operand of the unary expression.
     *
     * @param operand The operand to set for the unary expression.
     */
    public void setOperand(BoundExpression operand) {
        this._operand = operand;
    }

    /**
     * Gets the operator of the unary expression.
     *
     * @return The operator of the unary expression.
     */
    public BoundUnaryOperator getOperator() {
        return _operator;
    }
}