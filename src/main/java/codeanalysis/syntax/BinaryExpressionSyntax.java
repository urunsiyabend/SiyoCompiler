package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a binary expression in the syntax tree.
 * This class defines a binary expression, which consists of a left-hand side expression, an operator token,
 * and a right-hand side expression. It is used to represent expressions like "x + y" or "a * b".
 *
 * This class inherits from the ExpressionSyntax class and provides additional properties and methods
 * specific to binary expressions.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class BinaryExpressionSyntax extends ExpressionSyntax {
    private SyntaxToken _numberToken;
    private ExpressionSyntax left;
    private SyntaxToken operator;
    private ExpressionSyntax right;

    /**
     * Initializes a new instance of the BinaryExpressionSyntax class.
     *
     * @param left     The left-hand side expression.
     * @param operator The operator token.
     * @param right    The right-hand side expression.
     */
    public BinaryExpressionSyntax(ExpressionSyntax left, SyntaxToken operator, ExpressionSyntax right) {
        setLeft(left);
        setOperator(operator);
        setRight(right);
    }

    /**
     * Gets the number token.
     *
     * @return The number token.
     */
    public SyntaxToken getNumberToken() {
        return _numberToken;
    }

    /**
     * Sets the number token.
     *
     * @param _numberToken The number token to set.
     */
    public void setNumberToken(SyntaxToken _numberToken) {
        this._numberToken = _numberToken;
    }

    /**
     * Gets the left-hand side expression.
     *
     * @return The left-hand side expression.
     */
    public ExpressionSyntax getLeft() {
        return left;
    }

    /**
     * Sets the left-hand side expression.
     *
     * @param left The left-hand side expression to set.
     */
    public void setLeft(ExpressionSyntax left) {
        this.left = left;
    }

    /**
     * Gets the operator token.
     *
     * @return The operator token.
     */
    public SyntaxToken getOperator() {
        return operator;
    }

    /**
     * Sets the operator token.
     *
     * @param operator The operator token to set.
     */
    public void setOperator(SyntaxToken operator) {
        this.operator = operator;
    }

    /**
     * Gets the right-hand side expression.
     *
     * @return The right-hand side expression.
     */
    public ExpressionSyntax getRight() {
        return right;
    }

    /**
     * Sets the right-hand side expression.
     *
     * @param right The right-hand side expression to set.
     */
    public void setRight(ExpressionSyntax right) {
        this.right = right;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The syntax type of the binary expression.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.BinaryExpression;
    }

    /**
     * Returns an iterator over the child nodes of the binary expression.
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
            return index < 3;
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
                    return left;
                }
                case 1 -> {
                    index++;
                    return operator;
                }
                case 2 -> {
                    index++;
                    return right;
                }
                default -> throw new NoSuchElementException();
            }
        }

    }
}