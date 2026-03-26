package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents an array literal expression: [1, 2, 3]
 */
public class ArrayLiteralExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _openBracket;
    private final SeparatedSyntaxList<ExpressionSyntax> _elements;
    private final SyntaxToken _closeBracket;

    public ArrayLiteralExpressionSyntax(SyntaxToken openBracket, SeparatedSyntaxList<ExpressionSyntax> elements, SyntaxToken closeBracket) {
        _openBracket = openBracket;
        _elements = elements;
        _closeBracket = closeBracket;
    }

    public SyntaxToken getOpenBracket() { return _openBracket; }
    public SeparatedSyntaxList<ExpressionSyntax> getElements() { return _elements; }
    public SyntaxToken getCloseBracket() { return _closeBracket; }

    @Override
    public SyntaxType getType() {
        return SyntaxType.ArrayLiteralExpression;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int index = 0;
            @Override public boolean hasNext() { return index < 2; }
            @Override public SyntaxNode next() {
                return switch (index++) {
                    case 0 -> _openBracket;
                    case 1 -> _closeBracket;
                    default -> throw new NoSuchElementException();
                };
            }
        };
    }
}
