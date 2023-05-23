package com.urunsiyabend.codeanalysis;

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
    private final ExpressionSyntax _root;

    /**
     * Initializes a new instance of the {@code Evaluator} class with the specified root expression syntax.
     *
     * @param root The root expression syntax to evaluate.
     */
    public Evaluator(ExpressionSyntax root) {
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
    private int evaluateExpression(ExpressionSyntax node) throws Exception {
        if (node instanceof LiteralExpressionSyntax n) {
            return (int) n.getLiteralToken().getValue();
        }

        if (node instanceof BinaryExpressionSyntax b) {
            int left = evaluateExpression(b.getLeft());
            int right = evaluateExpression(b.getRight());

            if (b.getOperator().getType() == SyntaxType.PlusToken) {
                return left + right;
            }
            else if (b.getOperator().getType() == SyntaxType.MinusToken) {
                return left - right;
            }
            else if (b.getOperator().getType() == SyntaxType.AsteriskToken) {
                return left * right;
            }
            else if (b.getOperator().getType() == SyntaxType.SlashToken) {
                return left / right;
            }
            else {
                throw new Exception(String.format("Unexpected binary operator: %s", b.getOperator().getType()));
            }
        }

        if (node instanceof ParanthesizedExpressionSyntax p) {
            return evaluateExpression(p.getExpression());
        }
        throw new Exception(String.format("Unexpected node: %s", node.getType()));
    }
}
