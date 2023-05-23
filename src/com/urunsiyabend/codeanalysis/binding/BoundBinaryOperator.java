package com.urunsiyabend.codeanalysis.binding;

import com.urunsiyabend.codeanalysis.syntax.SyntaxType;


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

    private BoundBinaryOperator(SyntaxType syntaxType, BoundBinaryOperatorType type, Class<?> leftType) {

        _syntaxType = syntaxType;
        _type = type;
        _leftType = leftType;
        _rightType = leftType;
        _resultType = leftType;
    }

    private BoundBinaryOperator(SyntaxType syntaxType, BoundBinaryOperatorType type, Class<?> leftType, Class<?> resultType) {

        _syntaxType = syntaxType;
        _type = type;
        _leftType = leftType;
        _rightType = leftType;
        _resultType = resultType;
    }


    private BoundBinaryOperator(SyntaxType syntaxType, BoundBinaryOperatorType type, Class<?> leftType, Class<?> rightType, Class<?> resultType) {
        _syntaxType = syntaxType;
        _type = type;
        _leftType = leftType;
        _rightType = rightType;
        _resultType = resultType;
    }

    public static BoundBinaryOperator bind(SyntaxType syntaxType, Class<?> leftType, Class<?> rightType) {
        for (var op: _operators) {
            if (op.getSyntaxType() == syntaxType && op.getLeftType() == leftType && op.getRightType() == rightType) {
                return op;
            }
        }
        return null;
    }

    public SyntaxType getSyntaxType() {
        return _syntaxType;
    }

    public BoundBinaryOperatorType getType() {
        return _type;
    }

    public Class<?> getLeftType() {
        return _leftType;
    }

    public Class<?> getRightType() {
        return _rightType;
    }

    public Class<?> getResultType() {
        return _resultType;
    }
}
