package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a return statement in the syntax tree.
 * A return statement consists of the return keyword and an optional expression.
 * Example: "return a + b" or "return"
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ReturnStatementSyntax extends StatementSyntax {
    private final SyntaxToken _returnKeyword;
    private final ExpressionSyntax _expression;

    /**
     * Creates a new instance of the ReturnStatementSyntax class.
     *
     * @param returnKeyword The return keyword token.
     * @param expression    The optional expression to return (can be null).
     */
    public ReturnStatementSyntax(SyntaxToken returnKeyword, ExpressionSyntax expression) {
        _returnKeyword = returnKeyword;
        _expression = expression;
    }

    /**
     * Gets the return keyword token.
     *
     * @return The return keyword token.
     */
    public SyntaxToken getReturnKeyword() {
        return _returnKeyword;
    }

    /**
     * Gets the expression to return.
     *
     * @return The expression, or null if the return statement has no expression.
     */
    public ExpressionSyntax getExpression() {
        return _expression;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.ReturnStatement;
    }

    /**
     * Gets an iterator over the child nodes.
     *
     * @return An iterator over the child nodes.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    private class ChildrenIterator implements Iterator<SyntaxNode> {
        private int index;

        @Override
        public boolean hasNext() {
            return _expression == null ? index < 1 : index < 2;
        }

        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return _returnKeyword;
                }
                case 1 -> {
                    index++;
                    return _expression;
                }
                default -> throw new NoSuchElementException();
            }
        }
    }
}
