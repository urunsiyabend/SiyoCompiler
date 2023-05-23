package com.urunsiyabend.codeanalysis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a number expression in the syntax tree.
 * This class encapsulates a single number token.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class NumberExpressionSyntax extends ExpressionSyntax {
    private SyntaxToken _numberToken;

    /**
     * Initializes a new instance of the NumberExpressionSyntax class with the specified number token.
     *
     * @param numberToken The number token representing the value of the number expression.
     */
    public NumberExpressionSyntax(SyntaxToken numberToken) {
        setNumberToken(numberToken);
    }

    /**
     * Gets the syntax type of the number expression.
     *
     * @return The syntax type of the number expression.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.NumberExpression;
    }

    /**
     * Retrieves an iterator over the child nodes of the number expression.
     * In this case, the only child node is the number token.
     *
     * @return An iterator over the child nodes of the number expression.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        List<SyntaxNode> childNodes = new ArrayList<>();
        childNodes.add(_numberToken);
        return childNodes.iterator();
    }

    /**
     * Gets the number token representing the value of the number expression.
     *
     * @return The number token representing the value of the number expression.
     */
    public SyntaxToken getNumberToken() {
        return _numberToken;
    }

    /**
     * Sets the number token for the number expression.
     *
     * @param numberToken The number token representing the value of the number expression.
     */
    public void setNumberToken(SyntaxToken numberToken) {
        _numberToken = numberToken;
    }
}