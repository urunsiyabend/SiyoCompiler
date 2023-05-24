package com.urunsiyabend.codeanalysis.binding;

import com.urunsiyabend.codeanalysis.syntax.SyntaxType;

/**
 * The BoundBinaryOperator class represents a binary operator used in binding expressions.
 * It encapsulates information about the syntax type, operator type, left operand type, right operand type and result type of the operator.
 * The class provides methods to bind a binary operator based on the syntax type and operands type, and to retrieve the properties of an operator.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BoundBinaryOperator {
    private final SyntaxType _syntaxType;
    private final BoundBinaryOperatorType _type;
    private final Class<?> _leftType;
    private final Class<?> _rightType;
    private final Class<?> _resultType;
    private final static BoundBinaryOperator[] _operators = {
            new BoundBinaryOperator(SyntaxType.PlusToken, BoundBinaryOperatorType.Addition, Integer.class),
            new BoundBinaryOperator(SyntaxType.MinusToken, BoundBinaryOperatorType.Subtraction, Integer.class),
            new BoundBinaryOperator(SyntaxType.AsteriskToken, BoundBinaryOperatorType.Multiplication, Integer.class),
            new BoundBinaryOperator(SyntaxType.SlashToken, BoundBinaryOperatorType.Division, Integer.class),
            new BoundBinaryOperator(SyntaxType.EqualsEqualsToken, BoundBinaryOperatorType.Equals, Integer.class, Boolean.class),
            new BoundBinaryOperator(SyntaxType.BangEqualsToken, BoundBinaryOperatorType.NotEquals, Integer.class, Boolean.class),

            new BoundBinaryOperator(SyntaxType.DoubleAmpersandToken, BoundBinaryOperatorType.LogicalAnd, Boolean.class),
            new BoundBinaryOperator(SyntaxType.DoublePipeToken, BoundBinaryOperatorType.LogicalOr, Boolean.class),
            new BoundBinaryOperator(SyntaxType.EqualsEqualsToken, BoundBinaryOperatorType.Equals, Boolean.class, Boolean.class),
            new BoundBinaryOperator(SyntaxType.BangEqualsToken, BoundBinaryOperatorType.NotEquals, Boolean.class, Boolean.class),
    };

    /**
     * Constructs a BoundBinaryOperator with the specified syntax type, operator type, and left type.
     *
     * @param syntaxType The syntax type associated with the operator.
     * @param type The operator type.
     * @param leftType The left operand type.
     */
    private BoundBinaryOperator(SyntaxType syntaxType, BoundBinaryOperatorType type, Class<?> leftType) {

        _syntaxType = syntaxType;
        _type = type;
        _leftType = leftType;
        _rightType = leftType;
        _resultType = leftType;
    }

    /**
     * Constructs a BoundBinaryOperator with the specified syntax type, operator type, left type, and result type.
     *
     * @param syntaxType The syntax type associated with the operator.
     * @param type The operator type.
     * @param leftType The left operand type.
     * @param resultType The result type of the operation.
     */
    private BoundBinaryOperator(SyntaxType syntaxType, BoundBinaryOperatorType type, Class<?> leftType, Class<?> resultType) {

        _syntaxType = syntaxType;
        _type = type;
        _leftType = leftType;
        _rightType = leftType;
        _resultType = resultType;
    }

    /**
     * Constructs a BoundBinaryOperator with the specified syntax type, operator type, left type, right type, and result type.
     *
     * @param syntaxType The syntax type associated with the operator.
     * @param type The operator type.
     * @param leftType The left operand type.
     * @param rightType The right operand type.
     * @param resultType The result type of the operation.
     */
    private BoundBinaryOperator(SyntaxType syntaxType, BoundBinaryOperatorType type, Class<?> leftType, Class<?> rightType, Class<?> resultType) {
        _syntaxType = syntaxType;
        _type = type;
        _leftType = leftType;
        _rightType = rightType;
        _resultType = resultType;
    }

    /**
     * Binds a binary operator based on the syntax type, left type, and right type.
     *
     * @param syntaxType The syntax type of the operator.
     * @param leftType The left operand type.
     * @param rightType The right operand type.
     * @return The bound binary operator, or {@code null} if no matching operator is found.
     */
    public static BoundBinaryOperator bind(SyntaxType syntaxType, Class<?> leftType, Class<?> rightType) {
        for (var op: _operators) {
            if (op.getSyntaxType() == syntaxType && op.getLeftType() == leftType && op.getRightType() == rightType) {
                return op;
            }
        }
        return null;
    }

    /**
     * Gets the syntax type associated with the operator.
     *
     * @return The syntax type.
     */
    public SyntaxType getSyntaxType() {
        return _syntaxType;
    }

    /**
     * Gets the operator type.
     *
     * @return The operator type.
     */
    public BoundBinaryOperatorType getType() {
        return _type;
    }

    /**
     * Gets the left operand type.
     *
     * @return The left operand type.
     */
    public Class<?> getLeftType() {
        return _leftType;
    }

    /**
     * Gets the right operand type.
     *
     * @return The right operand type.
     */
    public Class<?> getRightType() {
        return _rightType;
    }

    /**
     * Gets the result type of the operation.
     *
     * @return The result type.
     */
    public Class<?> getResultType() {
        return _resultType;
    }
}
