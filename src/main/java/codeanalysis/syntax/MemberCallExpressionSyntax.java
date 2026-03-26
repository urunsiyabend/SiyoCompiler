package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a member call expression: module.func(args) or obj.method(args)
 */
public class MemberCallExpressionSyntax extends ExpressionSyntax {
    private final MemberAccessExpressionSyntax _memberAccess;
    private final SyntaxToken _openParen;
    private final SeparatedSyntaxList<ExpressionSyntax> _arguments;
    private final SyntaxToken _closeParen;

    public MemberCallExpressionSyntax(MemberAccessExpressionSyntax memberAccess, SyntaxToken openParen, SeparatedSyntaxList<ExpressionSyntax> arguments, SyntaxToken closeParen) {
        _memberAccess = memberAccess;
        _openParen = openParen;
        _arguments = arguments;
        _closeParen = closeParen;
    }

    public MemberAccessExpressionSyntax getMemberAccess() { return _memberAccess; }
    public SeparatedSyntaxList<ExpressionSyntax> getArguments() { return _arguments; }

    @Override
    public SyntaxType getType() { return SyntaxType.MemberCallExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int index = 0;
            @Override public boolean hasNext() { return index < 1; }
            @Override public SyntaxNode next() {
                if (index++ == 0) return _memberAccess;
                throw new NoSuchElementException();
            }
        };
    }
}
