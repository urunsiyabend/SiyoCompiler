package codeanalysis.syntax;

import java.util.Collections;
import java.util.Iterator;

/**
 * Represents a field assignment in a struct literal: name: value
 */
public class FieldAssignmentSyntax extends SyntaxNode {
    private final SyntaxToken _fieldName;
    private final SyntaxToken _colon;
    private final ExpressionSyntax _value;

    public FieldAssignmentSyntax(SyntaxToken fieldName, SyntaxToken colon, ExpressionSyntax value) {
        _fieldName = fieldName;
        _colon = colon;
        _value = value;
    }

    public SyntaxToken getFieldName() { return _fieldName; }
    public ExpressionSyntax getValue() { return _value; }

    @Override
    public SyntaxType getType() { return SyntaxType.FieldAssignment; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return Collections.<SyntaxNode>singletonList(_fieldName).iterator();
    }
}
