package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a try-catch statement: try { ... } catch err { ... }
 */
public class TryCatchStatementSyntax extends StatementSyntax {
    private final SyntaxToken _tryKeyword;
    private final StatementSyntax _tryBody;
    private final SyntaxToken _catchKeyword;
    private final SyntaxToken _errorVariable;
    private final StatementSyntax _catchBody;

    public TryCatchStatementSyntax(SyntaxToken tryKeyword, StatementSyntax tryBody,
                                    SyntaxToken catchKeyword, SyntaxToken errorVariable, StatementSyntax catchBody) {
        _tryKeyword = tryKeyword;
        _tryBody = tryBody;
        _catchKeyword = catchKeyword;
        _errorVariable = errorVariable;
        _catchBody = catchBody;
    }

    public StatementSyntax getTryBody() { return _tryBody; }
    public SyntaxToken getErrorVariable() { return _errorVariable; }
    public StatementSyntax getCatchBody() { return _catchBody; }

    @Override
    public SyntaxType getType() { return SyntaxType.TryCatchStatement; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_tryKeyword).iterator();
    }
}
