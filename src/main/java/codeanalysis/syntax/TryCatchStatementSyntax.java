package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a try-catch statement: try { ... } catch err { ... }
 *
 * <p>The error variable may carry a type annotation — {@code catch e: Result} —
 * which gives the bound payload a declared type, so a match over it can be
 * checked for exhaustiveness.
 */
public class TryCatchStatementSyntax extends StatementSyntax {
    private final SyntaxToken _tryKeyword;
    private final StatementSyntax _tryBody;
    private final SyntaxToken _catchKeyword;
    private final SyntaxToken _errorVariable;
    private final SyntaxToken _errorTypeToken;
    private final StatementSyntax _catchBody;

    public TryCatchStatementSyntax(SyntaxToken tryKeyword, StatementSyntax tryBody,
                                    SyntaxToken catchKeyword, SyntaxToken errorVariable, StatementSyntax catchBody) {
        this(tryKeyword, tryBody, catchKeyword, errorVariable, null, catchBody);
    }

    public TryCatchStatementSyntax(SyntaxToken tryKeyword, StatementSyntax tryBody,
                                    SyntaxToken catchKeyword, SyntaxToken errorVariable,
                                    SyntaxToken errorTypeToken, StatementSyntax catchBody) {
        _tryKeyword = tryKeyword;
        _tryBody = tryBody;
        _catchKeyword = catchKeyword;
        _errorVariable = errorVariable;
        _errorTypeToken = errorTypeToken;
        _catchBody = catchBody;
    }

    public StatementSyntax getTryBody() { return _tryBody; }
    public SyntaxToken getErrorVariable() { return _errorVariable; }

    /** The declared type of the error variable, or null when it has none. */
    public SyntaxToken getErrorTypeToken() { return _errorTypeToken; }
    public StatementSyntax getCatchBody() { return _catchBody; }

    @Override
    public SyntaxType getType() { return SyntaxType.TryCatchStatement; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_tryKeyword).iterator();
    }
}
