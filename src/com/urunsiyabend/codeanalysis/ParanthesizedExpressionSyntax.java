package com.urunsiyabend.codeanalysis;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a paranthesized expression in the syntax tree.
 * This class encapsulates an opening paranthesis token, an expression, and a closing paranthesis token.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class ParanthesizedExpressionSyntax extends ExpressionSyntax {
    private SyntaxToken _openParanthesisToken;
    private SyntaxToken _closeParanthesisToken;
    private ExpressionSyntax _expression;

    /**
     * Initializes a new instance of the ParanthesizedExpressionSyntax class with the specified tokens and expression.
     *
     * @param openParanthesisToken  The opening paranthesis token.
     * @param expression            The expression contained within the parantheses.
     * @param closeParanthesisToken The closing paranthesis token.
     */
    public ParanthesizedExpressionSyntax(SyntaxToken openParanthesisToken, ExpressionSyntax expression, SyntaxToken closeParanthesisToken) {
        _expression = expression;
        setOpenParanthesisToken(openParanthesisToken);
        setCloseParanthesisToken(closeParanthesisToken);
    }

    /**
     * Gets the syntax type of the paranthesized expression.
     *
     * @return The syntax type of the paranthesized expression.
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
     * Sets the opening paranthesis token for the paranthesized expression.
     *
     * @param openParanthesisToken The opening paranthesis token.
     */
    public void setOpenParanthesisToken(SyntaxToken openParanthesisToken) {
        _openParanthesisToken = openParanthesisToken;
    }

    /**
     * Gets the opening paranthesis token of the paranthesized expression.
     *
     * @return The opening paranthesis token.
     */
    public SyntaxToken getOpenParanthesisToken() {
        return _openParanthesisToken;
    }

    /**
     * Sets the closing paranthesis token for the paranthesized expression.
     *
     * @param closeParanthesisToken The closing paranthesis token.
     */
    public void setCloseParanthesisToken(SyntaxToken closeParanthesisToken) {
        _closeParanthesisToken = closeParanthesisToken;
    }

    /**
     * Gets the closing paranthesis token of the paranthesized expression.
     *
     * @return The closing paranthesis token.
     */
    public SyntaxToken getCloseParanthesisToken() {
        return _closeParanthesisToken;
    }

    /**
     * Gets the expression contained within the parantheses of the paranthesized expression.
     *
     * @return The expression within the parantheses.
     */
    public ExpressionSyntax getExpression() {
        return _expression;
    }

    /**
     * Sets the expression contained within the parantheses of the paranthesized expression.
     *
     * @param _expression The expression within the parantheses.
     */
    public void setExpression(ExpressionSyntax _expression) {
        this._expression = _expression;
    }

    /**
     * Iterator implementation for iterating over the child nodes of the paranthesized expression.
     * The child nodes include the opening paranthesis token and the closing paranthesis token.
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
                    return getOpenParanthesisToken();
                }
                case 1 -> {
                    index++;
                    return getCloseParanthesisToken();
                }
                default -> throw new NoSuchElementException();
            }
        }

    }
}