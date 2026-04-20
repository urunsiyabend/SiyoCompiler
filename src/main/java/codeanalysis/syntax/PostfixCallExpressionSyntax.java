package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents calling an arbitrary expression as a closure: expr(args).
 * Example: tests[i](arg1, arg2)
 */
public class PostfixCallExpressionSyntax extends ExpressionSyntax {
    private final ExpressionSyntax _callee;
    private final SyntaxToken _openParen;
    private final SeparatedSyntaxList<ExpressionSyntax> _arguments;
    private final SyntaxToken _closeParen;

    public PostfixCallExpressionSyntax(ExpressionSyntax callee, SyntaxToken openParen,
                                       SeparatedSyntaxList<ExpressionSyntax> arguments, SyntaxToken closeParen) {
        _callee = callee;
        _openParen = openParen;
        _arguments = arguments;
        _closeParen = closeParen;
    }

    public ExpressionSyntax getCallee() { return _callee; }
    public SeparatedSyntaxList<ExpressionSyntax> getArguments() { return _arguments; }

    @Override
    public SyntaxType getType() { return SyntaxType.PostfixCallExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int index = 0;
            @Override public boolean hasNext() { return index < 1; }
            @Override public SyntaxNode next() {
                if (index++ == 0) return _callee;
                throw new NoSuchElementException();
            }
        };
    }
}
