package codeanalysis.syntax;

import java.util.Iterator;

/**
 * Wraps a block statement as an expression (for match arms with block bodies).
 */
public class BlockExpressionSyntax extends ExpressionSyntax {
    private final StatementSyntax _block;

    public BlockExpressionSyntax(StatementSyntax block) {
        _block = block;
    }

    public StatementSyntax getBlock() { return _block; }

    @Override
    public SyntaxType getType() { return SyntaxType.LiteralExpression; } // reuse for now

    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new Iterator<>() {
            boolean done = false;
            @Override public boolean hasNext() { return !done; }
            @Override public SyntaxNode next() { done = true; return _block; }
        };
    }
}
