package com.urunsiyabend.codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a literal expression in the syntax tree.
 * This class encapsulates a single literal token.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public final class LiteralExpressionSyntax extends ExpressionSyntax {
    private final Object _value;
    private SyntaxToken _literalToken;


    /**
     * Initializes a new instance of the LiteralExpressionSyntax class with the specified literal token.
     *
     * @param literalToken The literal token representing the value of the literal expression.
     */
    public LiteralExpressionSyntax(SyntaxToken literalToken) {
        this._value = literalToken.getValue();
        setLiteralToken(literalToken);
    }

    /**
     * Initializes a new instance of the LiteralExpressionSyntax class with the specified literal token.
     *
     * @param literalToken The literal token representing the value of the literal expression.
     * @param value The value of literal expression.
     */
    public LiteralExpressionSyntax(SyntaxToken literalToken, Object value) {
        this._value = value;
        setLiteralToken(literalToken);
    }

    /**
     * Gets the syntax type of the literal expression.
     *
     * @return The syntax type of the literal expression.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.LiteralExpression;
    }

    /**
     * Retrieves an iterator over the child nodes of the literal expression.
     * In this case, the only child node is the literal token.
     *
     * @return An iterator over the child nodes of the literal expression.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        List<SyntaxNode> childNodes = new ArrayList<>();
        childNodes.add(_literalToken);
        return childNodes.iterator();
    }

    /**
     * Gets the literal token representing the value of the literal expression.
     *
     * @return The literal token representing the value of the literal expression.
     */
    public SyntaxToken getLiteralToken() {
        return _literalToken;
    }

    /**
     * Sets the literal token for the literal expression.
     *
     * @param literalToken The literal token representing the value of the literal expression.
     */
    public void setLiteralToken(SyntaxToken literalToken) {
        _literalToken = literalToken;
    }

    /**
     * Gets the value for the literal expression.
     *
     * @return The valueof the literal expression.
     */
    public Object getValue() {
        return _value;
    }
}