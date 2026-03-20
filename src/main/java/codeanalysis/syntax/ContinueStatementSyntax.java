package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a continue statement in the syntax tree.
 */
public class ContinueStatementSyntax extends StatementSyntax {
    private final SyntaxToken _keyword;

    public ContinueStatementSyntax(SyntaxToken keyword) {
        _keyword = keyword;
    }

    public SyntaxToken getKeyword() {
        return _keyword;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.ContinueStatement;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_keyword).iterator();
    }
}
