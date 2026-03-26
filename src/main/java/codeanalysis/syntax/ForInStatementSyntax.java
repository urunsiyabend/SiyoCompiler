package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a for-in statement: for item in collection { body }
 */
public class ForInStatementSyntax extends StatementSyntax {
    private final SyntaxToken _forKeyword;
    private final SyntaxToken _itemName;
    private final ExpressionSyntax _collection;
    private final StatementSyntax _body;

    public ForInStatementSyntax(SyntaxToken forKeyword, SyntaxToken itemName, ExpressionSyntax collection, StatementSyntax body) {
        _forKeyword = forKeyword;
        _itemName = itemName;
        _collection = collection;
        _body = body;
    }

    public SyntaxToken getItemName() { return _itemName; }
    public ExpressionSyntax getCollection() { return _collection; }
    public StatementSyntax getBody() { return _body; }

    @Override
    public SyntaxType getType() { return SyntaxType.ForInStatement; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_forKeyword).iterator();
    }
}
