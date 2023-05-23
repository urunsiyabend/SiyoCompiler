package com.urunsiyabend.codeanalysis;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a unary expression in the syntax tree.
 * This class defines a unary expression, which consists of a left-hand side expression, an operator token,
 * and a right-hand side expression. It is used to represent expressions like -a or !b.
 *
 * This class inherits from the ExpressionSyntax class and provides additional properties and methods
 * specific to Unary expressions.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class UnaryExpressionSyntax extends ExpressionSyntax {
    private SyntaxToken _operator;
    private ExpressionSyntax _operand;

    /**
     * Initializes a new instance of the UnaryExpressionSyntax class.
     *
     * @param operator The operator token.
     * @param operand The operand token.
     */
    public UnaryExpressionSyntax(SyntaxToken operator, ExpressionSyntax operand) {
        setOperator(operator);
        setOperand(operand);
    }

    /**
     * Gets the operator token.
     *
     * @return The operator token.
     */
    public SyntaxToken getOperator() {
        return _operator;
    }

    /**
     * Sets the operator token.
     *
     * @param operator The operator token to set.
     */
    public void setOperator(SyntaxToken operator) {
        this._operator = operator;
    }

    public ExpressionSyntax getOperand() {
        return _operand;
    }

    public void setOperand(ExpressionSyntax operand) {
        this._operand = operand;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The syntax type of the unary expression.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.UnaryExpression;
    }

    /**
     * Returns an iterator over the child nodes of the unary expression.
     *
     * @return An iterator over the child nodes.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    private class ChildrenIterator implements Iterator<SyntaxNode> {
        private int index;

        /**
         * Checks if there are more child nodes to iterate over.
         *
         * @return {@code true} if there are more child nodes, {@code false} otherwise.
         */
        @Override
        public boolean hasNext() {
            return index < 2;
        }

        /**
         * Returns the next child node.
         *
         * @return The next child node.
         * @throws NoSuchElementException if there are no more child nodes.
         */
        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return _operator;
                }
                case 1 -> {
                    index++;
                    return _operand;
                }
                default -> throw new NoSuchElementException();
            }
        }

    }
}