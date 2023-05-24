package com.urunsiyabend.codeanalysis.binding;

import com.urunsiyabend.codeanalysis.DiagnosticBox;
import com.urunsiyabend.codeanalysis.VariableSymbol;
import com.urunsiyabend.codeanalysis.syntax.*;

import java.util.Map;

/**
 * The `Binder` class is responsible for binding expression syntax to bound expressions.
 * It performs type checking and generates diagnostic messages for any binding errors.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Binder {
    private final Map<VariableSymbol, Object> _variables;
    DiagnosticBox _diagnostics = new DiagnosticBox();

    public Binder(Map<VariableSymbol, Object> variables) {
        this._variables = variables;
    }

    /**
     * Binds the given expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound expression.
     */
    public BoundExpression bindExpression(ExpressionSyntax syntax) {
        switch (syntax.getType()) {
            case ParenthesizedExpression -> {
                try {
                    return bindParenthesizedExpression(((ParanthesizedExpressionSyntax)syntax));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            case LiteralExpression -> {
                return bindLiteralExpression((LiteralExpressionSyntax)syntax);
            }
            case NameExpression -> {
                try {
                    return bindNameExpression(((NameExpressionSyntax)syntax));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            case AssignmentExpression -> {
                try {
                    return bindAssignmentExpression(((AssignmentExpressionSyntax)syntax));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
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
    public DiagnosticBox diagnostics() {
        return _diagnostics;
    }

    /**
     * Binds the given expression syntax to a bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindLiteralExpression(LiteralExpressionSyntax syntax) {
        Object value = syntax.getValue() != null ? syntax.getValue() : 0;
        return new BoundLiteralExpression(value);
    }


    /**
     * Binds unary expression syntax and returns the corresponding bound expression.
     *
     * @param syntax The expression syntax to bind.
     * @return The bound unary expression.
     */
    private BoundExpression bindUnaryExpression(UnaryExpressionSyntax syntax) throws Exception {
        BoundExpression boundOperand = bindExpression(syntax.getOperand());
        BoundUnaryOperator boundOperator = BoundUnaryOperator.bind(syntax.getOperator().getType(), boundOperand.getClassType());
        if (boundOperator == null) {
            _diagnostics.reportUndefinedUnaryOperator(syntax.getOperator().getSpan(), syntax.getOperator().getData(), boundOperand.getClassType());
            return boundOperand;
        }
        return new BoundUnaryExpression(boundOperator, boundOperand);
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
        BoundBinaryOperator boundOperator = BoundBinaryOperator.bind(syntax.getOperator().getType(), boundLeft.getClassType(), boundRight.getClassType());
        if (boundOperator == null) {
            _diagnostics.reportUndefinedBinaryOperator(syntax.getOperator().getSpan(), syntax.getOperator().getData(), boundLeft.getClassType(), boundRight.getClassType());
            return boundLeft;
        }
        return new BoundBinaryExpression(boundLeft, boundOperator, boundRight);
    }

    /**
     * Binds a parenthesized expression syntax to a bound expression.
     *
     * @param syntax The name expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindParenthesizedExpression(ParanthesizedExpressionSyntax syntax) {
        return bindExpression(syntax.getExpression());
    }

    /**
     * Binds a name expression syntax to a bound expression.
     *
     * @param syntax The name expression syntax to bind.
     * @return The bound expression.
     */
    private BoundExpression bindNameExpression(NameExpressionSyntax syntax) {
        String name = syntax.getIdentifierToken().getData();
        VariableSymbol variable = _variables.keySet()
                .stream()
                .filter(v -> v.getName().equals(name))
                .findFirst()
                .orElse(null);

        if (variable == null) {
            _diagnostics.reportUndefinedName(syntax.getIdentifierToken().getSpan(), name);
            return new BoundLiteralExpression(0);
        }
        return new BoundVariableExpression(variable);
    }

    /**
     * Binds an assignment expression syntax to a bound expression.
     *
     * @param syntax The assignment expression syntax to bind.
     * @return The bound expression.
     * @throws Exception If an error occurs during binding.
     */
    private BoundExpression bindAssignmentExpression(AssignmentExpressionSyntax syntax) throws Exception {
        String name = syntax.getIdentifierToken().getData();
        BoundExpression boundExpression = bindExpression(syntax.getExpressionSyntax());

        _variables.keySet()
                .stream()
                .filter(v -> v.getName().equals(name))
                .findFirst().ifPresent(_variables::remove);

        var variable = new VariableSymbol(name, boundExpression.getClassType());
        _variables.put(variable, null);

        return new BoundAssignmentExpression(variable, boundExpression);
    }
}
