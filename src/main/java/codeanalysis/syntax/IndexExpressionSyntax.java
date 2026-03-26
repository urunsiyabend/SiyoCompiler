package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents an index expression: arr[0] or s[0]
 */
public class IndexExpressionSyntax extends ExpressionSyntax {
    private final ExpressionSyntax _target;
    private final SyntaxToken _openBracket;
    private final ExpressionSyntax _index;
    private final SyntaxToken _closeBracket;

    public IndexExpressionSyntax(ExpressionSyntax target, SyntaxToken openBracket, ExpressionSyntax index, SyntaxToken closeBracket) {
        _target = target;
        _openBracket = openBracket;
        _index = index;
        _closeBracket = closeBracket;
    }

    public ExpressionSyntax getTarget() { return _target; }
    public SyntaxToken getOpenBracket() { return _openBracket; }
    public ExpressionSyntax getIndex() { return _index; }
    public SyntaxToken getCloseBracket() { return _closeBracket; }

    @Override
    public SyntaxType getType() {
        return SyntaxType.IndexExpression;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int index = 0;
            @Override public boolean hasNext() { return index < 4; }
            @Override public SyntaxNode next() {
                return switch (index++) {
                    case 0 -> _target;
                    case 1 -> _openBracket;
                    case 2 -> _index;
                    case 3 -> _closeBracket;
                    default -> throw new NoSuchElementException();
                };
            }
        };
    }
}
