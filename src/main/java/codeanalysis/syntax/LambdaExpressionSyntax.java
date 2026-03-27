package codeanalysis.syntax;

import java.util.Iterator;
import java.util.List;

/**
 * fn(x: int, y: int) -> int { x + y }
 */
public class LambdaExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _fnKeyword;
    private final SyntaxToken _openParen;
    private final SeparatedSyntaxList<ParameterSyntax> _parameters;
    private final SyntaxToken _closeParen;
    private final TypeClauseSyntax _typeClause; // nullable (return type)
    private final StatementSyntax _body;

    public LambdaExpressionSyntax(SyntaxToken fnKeyword, SyntaxToken openParen,
                                   SeparatedSyntaxList<ParameterSyntax> parameters,
                                   SyntaxToken closeParen, TypeClauseSyntax typeClause,
                                   StatementSyntax body) {
        _fnKeyword = fnKeyword;
        _openParen = openParen;
        _parameters = parameters;
        _closeParen = closeParen;
        _typeClause = typeClause;
        _body = body;
    }

    public SyntaxToken getFnKeyword() { return _fnKeyword; }
    public SeparatedSyntaxList<ParameterSyntax> getParameters() { return _parameters; }
    public TypeClauseSyntax getTypeClause() { return _typeClause; }
    public StatementSyntax getBody() { return _body; }

    @Override
    public SyntaxType getType() { return SyntaxType.LambdaExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return List.<SyntaxNode>of(_fnKeyword, _body).iterator();
    }
}
