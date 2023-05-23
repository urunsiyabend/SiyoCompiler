package com.urunsiyabend.codeanalysis.binding;

/**
 * Represents a bound unary expression in the code analysis process.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundUnaryExpression extends BoundExpression {
    private BoundUnaryOperatorType _operatorType;
    private BoundExpression _operand;

    /**
     * Constructs a new instance of the BoundUnaryExpression class.
     *
     * @param operatorType The operator type of the unary expression.
     * @param operand      The operand of the unary expression.
     */
    public BoundUnaryExpression(BoundUnaryOperatorType operatorType, BoundExpression operand) {
        _operatorType = operatorType;
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
        return getOperand().getClassType();
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
     * Gets the operator type of the unary expression.
     *
     * @return The operator type of the unary expression.
     */
    public BoundUnaryOperatorType getOperatorType() {
        return _operatorType;
    }

    /**
     * Sets the operator type of the unary expression.
     *
     * @param operatorType The operator type to set for the unary expression.
     */
    public void setOperatorType(BoundUnaryOperatorType operatorType) {
        this._operatorType = operatorType;
    }
}