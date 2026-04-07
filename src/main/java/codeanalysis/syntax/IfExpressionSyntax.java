package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents an `if`/`else` form used in expression position.
 * Wraps an underlying {@link IfStatementSyntax} so the binder can desugar it
 * into a value-producing expression while reusing the existing parser logic.
 */
public class IfExpressionSyntax extends ExpressionSyntax {
    private final IfStatementSyntax _ifStatement;

    public IfExpressionSyntax(IfStatementSyntax ifStatement) {
        _ifStatement = ifStatement;
    }

    public IfStatementSyntax getIfStatement() {
        return _ifStatement;
    }

    @Override
    public SyntaxType getType() {
        return SyntaxType.IfExpression;
    }

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            private boolean done = false;
            @Override public boolean hasNext() { return !done; }
            @Override public SyntaxNode next() {
                if (done) throw new NoSuchElementException();
                done = true;
                return _ifStatement;
            }
        };
    }
}
