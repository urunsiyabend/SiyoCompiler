package codeanalysis.syntax;

import java.util.Iterator;
import java.util.List;

public class CastExpressionSyntax extends ExpressionSyntax {
    private final ExpressionSyntax _expression;
    private final SyntaxToken _asKeyword;
    private final SyntaxToken _typeName;

    public CastExpressionSyntax(ExpressionSyntax expression, SyntaxToken asKeyword, SyntaxToken typeName) {
        _expression = expression;
        _asKeyword = asKeyword;
        _typeName = typeName;
    }

    public ExpressionSyntax getExpression() { return _expression; }
    public SyntaxToken getAsKeyword() { return _asKeyword; }
    public SyntaxToken getTypeName() { return _typeName; }

    @Override
    public SyntaxType getType() { return SyntaxType.CastExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return List.<SyntaxNode>of(_expression, _asKeyword, _typeName).iterator();
    }
}
