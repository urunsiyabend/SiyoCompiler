package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a break statement in the syntax tree.
 */
public class BreakStatementSyntax extends StatementSyntax {
    private final SyntaxToken _keyword;

    public BreakStatementSyntax(SyntaxToken keyword) {
        _keyword = keyword;
    }

    public SyntaxToken getKeyword() {
        return _keyword;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.BreakStatement;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_keyword).iterator();
    }
}
