package com.urunsiyabend.codeanalysis;

import com.urunsiyabend.codeanalysis.binding.*;
import com.urunsiyabend.codeanalysis.syntax.*;

/**
 * The {@code Evaluator} class is responsible for evaluating an expression syntax tree and computing the result.
 * It provides a method to evaluate the root expression syntax and return the computed result.
 *
 * The evaluator supports evaluating different types of expressions, including numbers, binary expressions
 * (addition, subtraction, multiplication, and division), and parenthesized expressions.
 *
 *
 * Note: The evaluator throws an exception if it encounters an unexpected node or operator during evaluation.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Evaluator {
    private final BoundExpression _root;

    /**
     * Initializes a new instance of the {@code Evaluator} class with the specified root expression syntax.
     *
     * @param root The root expression syntax to evaluate.
     */
    public Evaluator(BoundExpression root) {
        _root = root;
    }

    /**
     * Evaluates the expression syntax tree and computes the result.
     *
     * @return The computed result of the expression.
     * @throws Exception if an error occurs during evaluation.
     */
    public int evaluate() throws Exception {
        return evaluateExpression(_root);
    }

    /**
     * Recursively evaluates the specified expression syntax node and computes the result.
     *
     * @param node The expression syntax node to evaluate.
     * @return The computed result of the expression.
     * @throws Exception if an error occurs during evaluation or if an unexpected node is encountered.
     */
    private int evaluateExpression(BoundExpression node) throws Exception {
        if (node instanceof BoundLiteralExpression n) {
            return (int) n.getValue();
        }

        if (node instanceof BoundUnaryExpression u) {
            int operand = evaluateExpression(u.getOperand());
            return switch (u.getOperatorType()) {
                case Identity -> operand;
                case Negation -> -operand;
                default -> throw new Exception(String.format("Unexpected unary operator: %s", u.getOperatorType()));
            };
        }


        if (node instanceof BoundBinaryExpression b) {
            int left = evaluateExpression(b.getLeft());
            int right = evaluateExpression(b.getRight());

            return switch (b.getOperatorType()) {
                case Addition -> left + right;
                case Subtraction -> left - right;
                case Multiplication -> left * right;
                case Division -> left / right;
                default -> throw new Exception(String.format("Unexpected binary operator: %s", b.getOperatorType()));
            };
        }

        throw new Exception(String.format("Unexpected node: %s", node.getType()));
    }
}
