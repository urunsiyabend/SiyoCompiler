package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Class that represents a for statement in the syntax tree.
 * A for statement consists of a for keyword, an initializer, a condition, an iterator, and a body.
 * The initializer is a variable declaration, the condition is an expression, the iterator is an expression,
 * and the body is a statement.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ForStatementSyntax extends StatementSyntax {
    private final SyntaxToken _forKeyword;
    private final StatementSyntax _initializer;
    private final ExpressionSyntax _condition;
    private final ExpressionSyntax _iterator;
    private final StatementSyntax _body;

    /**
     * Initializes a new instance of the {@code ForStatementSyntax} class with the specified for keyword, initializer, condition, iterator, and body.
     * The initializer is a variable declaration, the condition is an expression, the iterator is an expression,
     * and the body is a statement.
     *
     * @param forKeyword The for keyword.
     * @param initializer The initializer.
     * @param condition The condition.
     * @param iterator The iterator.
     * @param body The body.
     */
    public ForStatementSyntax(SyntaxToken forKeyword, StatementSyntax initializer, ExpressionSyntax condition, ExpressionSyntax iterator, StatementSyntax body) {
        _forKeyword = forKeyword;
        _initializer = initializer;
        _condition = condition;
        _iterator = iterator;
        _body = body;
    }

    /**
     * Retrieves the type of the syntax node.
     *
     * @return The syntax node type.
     */
    public SyntaxToken getForKeyword() {
        return _forKeyword;
    }

    /**
     * Retrieves the initializer.
     *
     * @return The initializer.
     */
    public StatementSyntax getInitializer() {
        return _initializer;
    }

    /**
     * Retrieves the condition.
     *
     * @return The condition.
     */
    public ExpressionSyntax getCondition() {
        return _condition;
    }

    /**
     * Retrieves the iterator.
     *
     * @return The iterator.
     */
    public ExpressionSyntax getIterator() {
        return _iterator;
    }

    /**
     * Retrieves the body.
     *
     * @return The body.
     */
    public StatementSyntax getBody() {
        return _body;
    }

    /**
     * Retrieves the type of the syntax node.
     *
     * @return The syntax node type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.ForStatement;
    }

    /**
     * Retrieves the children of the syntax node.
     *
     * @return The children of the syntax node.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    /**
     * Iterator class that iterates over the children of the syntax node.
     */
    private class ChildrenIterator implements Iterator<SyntaxNode> {
        private int _index = 0;

        /**
         * Checks whether there is a next element in the iterator.
         *
         * @return True if there is a next element, false otherwise.
         */
        @Override
        public boolean hasNext() {
            return _index < 5;
        }

        /**
         * Retrieves the next element in the iterator.
         *
         * @return The next element.
         */
        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            return switch (_index++) {
                case 0 -> _forKeyword;
                case 1 -> _initializer;
                case 2 -> _condition;
                case 3 -> _iterator;
                case 4 -> _body;
                default -> throw new NoSuchElementException();
            };
        }
    }
}
