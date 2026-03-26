package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a struct literal expression: Point { x: 1, y: 2 }
 */
public class StructLiteralExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _typeName;
    private final SyntaxToken _openBrace;
    private final List<SyntaxNode> _fieldAssignments;
    private final SyntaxToken _closeBrace;

    public StructLiteralExpressionSyntax(SyntaxToken typeName, SyntaxToken openBrace, List<SyntaxNode> fieldAssignments, SyntaxToken closeBrace) {
        _typeName = typeName;
        _openBrace = openBrace;
        _fieldAssignments = fieldAssignments;
        _closeBrace = closeBrace;
    }

    public SyntaxToken getTypeName() { return _typeName; }
    public List<SyntaxNode> getFieldAssignments() { return _fieldAssignments; }

    @Override
    public SyntaxType getType() { return SyntaxType.StructLiteralExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_typeName).iterator();
    }
}
