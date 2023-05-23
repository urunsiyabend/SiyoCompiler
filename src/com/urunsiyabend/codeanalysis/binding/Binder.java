package com.urunsiyabend.codeanalysis.binding;

import com.urunsiyabend.codeanalysis.syntax.*;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * The `Binder` class is responsible for binding expression syntax to bound expressions.
 * It performs type checking and generates diagnostic messages for any binding errors.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Binder {
    ArrayList<String> _diagnostics = new ArrayList<>();

    /**
     * Binds the given expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound expression.
     */
    public BoundExpression bindExpression(ExpressionSyntax syntax) {
        switch (syntax.getType()) {
            case LiteralExpression -> {
                return bindLiteralExpression((LiteralExpressionSyntax)syntax);
            }
            case UnaryExpression -> {
                try {
                    return bindUnaryExpression((UnaryExpressionSyntax)syntax);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            case BinaryExpression -> {
                try {
                    return bindBinaryExpression((BinaryExpressionSyntax)syntax);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
        return null;
    }

    /**
     * Gets an iterator over the diagnostic messages generated during binding.
     *
     * @return An iterator over the diagnostic messages.
     */
    public Iterator<String> diagnostics() {
        return _diagnostics.iterator();
    }

    /**
     * Binds the given expression syntax to a bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindLiteralExpression(LiteralExpressionSyntax syntax) {
        Object value = syntax.getLiteralToken().getValue();
        int intValue = (value instanceof Integer) ? (int) value : 0;
        return new BoundLiteralExpression(intValue);
    }


    /**
     * Binds unary expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound unary expression.
     */
    private BoundExpression bindUnaryExpression(UnaryExpressionSyntax syntax) throws Exception {
        BoundExpression boundOperand = bindExpression(syntax.getOperand());
        BoundUnaryOperatorType boundOperatorType = bindUnaryOperatorType(syntax.getOperand().getType(), boundOperand.getClassType());
        if (boundOperatorType == null) {
            _diagnostics.add(String.format("Unary operator '%s' is not defined for type %s", syntax.getOperator(), boundOperand.getClassType()));
        }
        return new BoundUnaryExpression(boundOperatorType, boundOperand);
    }

    /**
     * Binds binary expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound binary expression.
     */
    private BoundExpression bindBinaryExpression(BinaryExpressionSyntax syntax) throws Exception {
        BoundExpression boundLeft = bindExpression(syntax.getLeft());
        BoundExpression boundRight = bindExpression(syntax.getRight());
        BoundBinaryOperatorType boundOperatorType = bindBinaryOperatorType(syntax.getOperator().getType(), boundLeft.getClassType(), boundRight.getClassType());
        if (boundOperatorType == null) {
            _diagnostics.add(String.format("Binary operator '%s' is not defined for types %s and %s", syntax.getOperator(), boundLeft.getClassType(), boundRight.getClassType()));
        }
        return new BoundBinaryExpression(boundLeft, boundOperatorType, boundRight);
    }

    /**
     * Binds the given unary operator type to a bound unary operator type based on the syntax type and operand type.
     *
     * @param type        The syntax type of the unary operator.
     * @param operandType The type of the operand.
     * @return The bound unary operator type.
     * @throws Exception if the unary operator type is unexpected.
     */
    private BoundUnaryOperatorType bindUnaryOperatorType(SyntaxType type, Class<?> operandType) throws Exception {
        if (operandType != int.class) {
            return null;
        }
        return switch (type) {
            case PlusToken -> BoundUnaryOperatorType.Identity;
            case MinusToken -> BoundUnaryOperatorType.Negation;
            default -> throw new Exception(String.format("Unexpected unary operator <%s>", type));
        };
    }

    /**
     * Binds the given binary operator type to a bound binary operator type based on the syntax type, left operand type, and right operand type.
     *
     * @param type       The syntax type of the binary operator.
     * @param leftType   The type of the left operand.
     * @param rightType  The type of the right operand.
     * @return The bound binary operator type.
     * @throws Exception if the binary operator type is unexpected.
     */
    private BoundBinaryOperatorType bindBinaryOperatorType(SyntaxType type, Class<?> leftType, Class<?> rightType) throws Exception {
        if (leftType != int.class || rightType != int.class) {
            return null;
        }

        return switch (type) {
            case PlusToken -> BoundBinaryOperatorType.Addition;
            case MinusToken -> BoundBinaryOperatorType.Subtraction;
            case AsteriskToken -> BoundBinaryOperatorType.Multiplication;
            case SlashToken -> BoundBinaryOperatorType.Division;
            default -> throw new Exception(String.format("Unexpected binary operator <%s>", type));
        };
    }

}