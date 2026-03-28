package codeanalysis.syntax;

import java.util.Iterator;
import java.util.List;

/**
 * spawn { ... }
 * Launches a concurrent task. Must be inside a scope block.
 */
public class SpawnExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _spawnKeyword;
    private final StatementSyntax _body;

    public SpawnExpressionSyntax(SyntaxToken spawnKeyword, StatementSyntax body) {
        _spawnKeyword = spawnKeyword;
        _body = body;
    }

    public SyntaxToken getSpawnKeyword() { return _spawnKeyword; }
    public StatementSyntax getBody() { return _body; }

    @Override
    public SyntaxType getType() { return SyntaxType.SpawnExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return List.<SyntaxNode>of(_spawnKeyword, _body).iterator();
    }
}
