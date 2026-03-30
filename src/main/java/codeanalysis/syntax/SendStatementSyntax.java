package codeanalysis.syntax;

import java.util.Iterator;

/**
 * send actor.method(args) — fire-and-forget actor dispatch.
 */
public class SendStatementSyntax extends StatementSyntax {
    private final SyntaxToken _keyword;
    private final ExpressionSyntax _expression;

    public SendStatementSyntax(SyntaxToken keyword, ExpressionSyntax expression) {
        _keyword = keyword;
        _expression = expression;
    }

    public SyntaxToken getKeyword() { return _keyword; }
    public ExpressionSyntax getExpression() { return _expression; }

    @Override
    public SyntaxType getType() { return SyntaxType.SendStatement; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int idx = 0;
            @Override public boolean hasNext() { return idx < 2; }
            @Override public SyntaxNode next() {
                return switch (idx++) {
                    case 0 -> _keyword;
                    case 1 -> _expression;
                    default -> throw new java.util.NoSuchElementException();
                };
            }
        };
    }
}
