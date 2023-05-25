package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a parenthesized expression in the syntax tree.
 * This class encapsulates an opening parenthesis token, an expression, and a closing parenthesis token.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class ParanthesizedExpressionSyntax extends ExpressionSyntax {
    private SyntaxToken _openParenthesisToken;
    private SyntaxToken _closeParenthesisToken;
    private ExpressionSyntax _expression;

    /**
     * Initializes a new instance of the ParenthesizedExpressionSyntax class with the specified tokens and expression.
     *
     * @param openParenthesisToken  The opening parenthesis token.
     * @param expression            The expression contained within the parentheses.
     * @param closeParenthesisToken The closing parenthesis token.
     */
    public ParanthesizedExpressionSyntax(SyntaxToken openParenthesisToken, ExpressionSyntax expression, SyntaxToken closeParenthesisToken) {
        _expression = expression;
        setOpenParenthesisToken(openParenthesisToken);
        setCloseParenthesisToken(closeParenthesisToken);
    }

    /**
     * Gets the syntax type of the parenthesized expression.
     *
     * @return The syntax type of the parenthesized expression.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.ParenthesizedExpression;
    }

    /**
     * Retrieves an iterator over the child nodes of the paranthesized expression.
     * The child nodes include the opening paranthesis token, expression, and closing paranthesis token.
     *
     * @return An iterator over the child nodes of the paranthesized expression.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ParanthesizedExpressionSyntax.ChildrenIterator();
    }

    /**
     * Sets the opening parenthesis token for the paranthesized expression.
     *
     * @param openParenthesisToken The opening parenthesis token.
     */
    public void setOpenParenthesisToken(SyntaxToken openParenthesisToken) {
        _openParenthesisToken = openParenthesisToken;
    }

    /**
     * Gets the opening parenthesis token of the parenthesized expression.
     *
     * @return The opening parenthesis token.
     */
    public SyntaxToken getOpenParenthesisToken() {
        return _openParenthesisToken;
    }

    /**
     * Sets the closing parenthesis token for the paranthesized expression.
     *
     * @param closeParenthesisToken The closing paranthesis token.
     */
    public void setCloseParenthesisToken(SyntaxToken closeParenthesisToken) {
        _closeParenthesisToken = closeParenthesisToken;
    }

    /**
     * Gets the closing parenthesis token of the parenthesized expression.
     *
     * @return The closing parenthesis token.
     */
    public SyntaxToken getCloseParenthesisToken() {
        return _closeParenthesisToken;
    }

    /**
     * Gets the expression contained within the parentheses of the parenthesized expression.
     *
     * @return The expression within the parentheses.
     */
    public ExpressionSyntax getExpression() {
        return _expression;
    }

    /**
     * Sets the expression contained within the parentheses of the parenthesized expression.
     *
     * @param _expression The expression within the parentheses.
     */
    public void setExpression(ExpressionSyntax _expression) {
        this._expression = _expression;
    }

    /**
     * Iterator implementation for iterating over the child nodes of the parenthesized expression.
     * The child nodes include the opening parenthesis token and the closing parenthesis token.
     */
    private class ChildrenIterator implements Iterator<SyntaxNode> {

        private int index;

        /**
         * Checks if there are more child nodes to iterate over.
         *
         * @return true if there are more child nodes; otherwise, false.
         */
        @Override
        public boolean hasNext() {
            return index < 2;
        }

        /**
         * Retrieves the next child node.
         *
         * @return The next child node.
         * @throws NoSuchElementException if there are no more child nodes to iterate over.
         */
        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return getOpenParenthesisToken();
                }
                case 1 -> {
                    index++;
                    return getCloseParenthesisToken();
                }
                default -> throw new NoSuchElementException();
            }
        }

    }
}