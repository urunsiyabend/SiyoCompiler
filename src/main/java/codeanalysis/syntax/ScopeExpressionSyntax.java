package codeanalysis.syntax;

import java.util.Iterator;
import java.util.List;

/**
 * scope { spawn { ... } spawn { ... } }
 * All spawns must complete before scope exits.
 */
public class ScopeExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _scopeKeyword;
    private final StatementSyntax _body;

    public ScopeExpressionSyntax(SyntaxToken scopeKeyword, StatementSyntax body) {
        _scopeKeyword = scopeKeyword;
        _body = body;
    }

    public SyntaxToken getScopeKeyword() { return _scopeKeyword; }
    public StatementSyntax getBody() { return _body; }

    @Override
    public SyntaxType getType() { return SyntaxType.ScopeExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return List.<SyntaxNode>of(_scopeKeyword, _body).iterator();
    }
}
