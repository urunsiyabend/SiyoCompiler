package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a throw statement in the syntax tree.
 * A throw statement raises an arbitrary value as an error.
 * Example: "throw \"boom\"" or "throw NotFound(404)"
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ThrowStatementSyntax extends StatementSyntax {
    private final SyntaxToken _throwKeyword;
    private final ExpressionSyntax _expression;

    /**
     * Creates a new instance of the ThrowStatementSyntax class.
     *
     * @param throwKeyword The throw keyword token.
     * @param expression   The value to raise.
     */
    public ThrowStatementSyntax(SyntaxToken throwKeyword, ExpressionSyntax expression) {
        _throwKeyword = throwKeyword;
        _expression = expression;
    }

    /**
     * Gets the throw keyword token.
     *
     * @return The throw keyword token.
     */
    public SyntaxToken getThrowKeyword() {
        return _throwKeyword;
    }

    /**
     * Gets the expression whose value is raised.
     *
     * @return The expression.
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
        return SyntaxType.ThrowStatement;
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
            return index < 2;
        }

        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return _throwKeyword;
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
