package codeanalysis.syntax;

import java.util.Iterator;

/**
 * try { expr } catch e { fallback } — as an expression that returns a value.
 */
public class TryExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _tryKeyword;
    private final StatementSyntax _tryBody;
    private final SyntaxToken _catchKeyword;
    private final SyntaxToken _errorVariable;
    private final StatementSyntax _catchBody;

    public TryExpressionSyntax(SyntaxToken tryKeyword, StatementSyntax tryBody,
                                SyntaxToken catchKeyword, SyntaxToken errorVariable,
                                StatementSyntax catchBody) {
        _tryKeyword = tryKeyword;
        _tryBody = tryBody;
        _catchKeyword = catchKeyword;
        _errorVariable = errorVariable;
        _catchBody = catchBody;
    }

    public SyntaxToken getTryKeyword() { return _tryKeyword; }
    public StatementSyntax getTryBody() { return _tryBody; }
    public SyntaxToken getCatchKeyword() { return _catchKeyword; }
    public SyntaxToken getErrorVariable() { return _errorVariable; }
    public StatementSyntax getCatchBody() { return _catchBody; }

    @Override
    public SyntaxType getType() { return SyntaxType.TryExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int idx = 0;
            @Override public boolean hasNext() { return idx < 5; }
            @Override public SyntaxNode next() {
                return switch (idx++) {
                    case 0 -> _tryKeyword;
                    case 1 -> _tryBody;
                    case 2 -> _catchKeyword;
                    case 3 -> _errorVariable;
                    case 4 -> _catchBody;
                    default -> throw new java.util.NoSuchElementException();
                };
            }
        };
    }
}
