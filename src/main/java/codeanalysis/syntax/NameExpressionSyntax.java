package codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The NameExpressionSyntax class represents a name expression in the syntax tree.
 * It consists of an identifier token within its value.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class NameExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _identifierToken;

    /**
     * Creates a new instance of the NameExpressionSyntax class with the specified identifier token
     *
     * @param identifierToken   The identifier token.
     */
    public NameExpressionSyntax(SyntaxToken identifierToken) {
        this._identifierToken = identifierToken;
    }

    /**
     * Retrieves the identifier token.
     *
     * @return The identifier token.
     */
    public SyntaxToken getIdentifierToken() {
        return _identifierToken;
    }

    /**
     * Retrieves the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.NameExpression;
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
        childNodes.add(_identifierToken);
        return childNodes.iterator();
    }
}
