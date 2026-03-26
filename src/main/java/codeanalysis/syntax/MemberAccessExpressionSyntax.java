package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a member access expression: p.x
 */
public class MemberAccessExpressionSyntax extends ExpressionSyntax {
    private final ExpressionSyntax _target;
    private final SyntaxToken _dot;
    private final SyntaxToken _member;

    public MemberAccessExpressionSyntax(ExpressionSyntax target, SyntaxToken dot, SyntaxToken member) {
        _target = target;
        _dot = dot;
        _member = member;
    }

    public ExpressionSyntax getTarget() { return _target; }
    public SyntaxToken getDot() { return _dot; }
    public SyntaxToken getMember() { return _member; }

    @Override
    public SyntaxType getType() {
        return SyntaxType.MemberAccessExpression;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int index = 0;
            @Override public boolean hasNext() { return index < 3; }
            @Override public SyntaxNode next() {
                return switch (index++) {
                    case 0 -> _target;
                    case 1 -> _dot;
                    case 2 -> _member;
                    default -> throw new NoSuchElementException();
                };
            }
        };
    }
}
