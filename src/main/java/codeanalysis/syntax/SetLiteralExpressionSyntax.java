package codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A set literal: {@code #{1, 2, 3}}, or {@code #{}} for an empty set.
 *
 * <p>The leading {@code #} keeps it apart from a map literal, which is written
 * with the same braces.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class SetLiteralExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _hash;
    private final SyntaxToken _openBrace;
    private final List<ExpressionSyntax> _elements;
    private final SyntaxToken _closeBrace;

    public SetLiteralExpressionSyntax(SyntaxToken hash, SyntaxToken openBrace,
                                      List<ExpressionSyntax> elements, SyntaxToken closeBrace) {
        _hash = hash;
        _openBrace = openBrace;
        _elements = elements;
        _closeBrace = closeBrace;
    }

    /**
     * Gets the elements of the set.
     *
     * @return The element expressions, in the order written.
     */
    public List<ExpressionSyntax> getElements() {
        return _elements;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.SetLiteralExpression;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        List<SyntaxNode> children = new ArrayList<>();
        children.add(_hash);
        children.add(_openBrace);
        children.addAll(_elements);
        children.add(_closeBrace);
        return children.iterator();
    }
}
