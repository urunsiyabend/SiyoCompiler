package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a compound assignment: arr[0] = 5 or p.x = 10
 */
public class CompoundAssignmentExpressionSyntax extends ExpressionSyntax {
    private final ExpressionSyntax _target;
    private final SyntaxToken _equalsToken;
    private final ExpressionSyntax _value;

    public CompoundAssignmentExpressionSyntax(ExpressionSyntax target, SyntaxToken equalsToken, ExpressionSyntax value) {
        _target = target;
        _equalsToken = equalsToken;
        _value = value;
    }

    public ExpressionSyntax getTarget() { return _target; }
    public SyntaxToken getEqualsToken() { return _equalsToken; }
    public ExpressionSyntax getValue() { return _value; }

    @Override
    public SyntaxType getType() { return SyntaxType.CompoundAssignmentExpression; }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            int index = 0;
            @Override public boolean hasNext() { return index < 3; }
            @Override public SyntaxNode next() {
                return switch (index++) {
                    case 0 -> _target;
                    case 1 -> _equalsToken;
                    case 2 -> _value;
                    default -> throw new NoSuchElementException();
                };
            }
        };
    }
}
