package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a while statement in the syntax tree.
 * This class defines a while statement, which consists of a keyword token, a condition expression,
 * and a body statement.
 * It is used to represent statements like "while (true) { }".
 * <p>
 * This class inherits from the StatementSyntax class and provides additional properties and methods
 * specific to while statements.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class WhileStatementSyntax extends StatementSyntax {
    private final SyntaxToken _keyword;
    private final ExpressionSyntax _condition;
    private final StatementSyntax _body;

    /**
     * Initializes a new instance of the WhileStatementSyntax class.
     *
     * @param keyword   The keyword token.
     * @param condition The condition expression.
     * @param body      The body statement.
     */
    public WhileStatementSyntax(SyntaxToken keyword, ExpressionSyntax condition, StatementSyntax body) {
        _keyword = keyword;
        _condition = condition;
        _body = body;
    }

    /**
     * Gets the keyword token.
     *
     * @return The keyword token of the while statement.
     */
    public SyntaxToken getKeyword() {
        return _keyword;
    }

    /**
     * Gets the condition expression.
     *
     * @return The condition expression of the while statement.
     */
    public ExpressionSyntax getCondition() {
        return _condition;
    }

    /**
     * Gets the body statement.
     *
     * @return The body statement of the while statement.
     */
    public StatementSyntax getBody() {
        return _body;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The type of the syntax node.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.WhileStatement;
    }

    /**
     * Gets the children of the syntax node.
     *
     * @return The children of the syntax node.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    /**
     * This class implements the Iterator interface and is used to iterate over the child nodes of the syntax node.
     * It is used by the getChildren method.
     *
     * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
     * @author Siyabend Urun
     * @version 1.0
     */
    private class ChildrenIterator implements Iterator<SyntaxNode> {
        private int index;

        /**
         * Checks if there are more child nodes to iterate over.
         *
         * @return {@code true} if there are more child nodes, {@code false} otherwise.
         */
        @Override
        public boolean hasNext() {
            return index < 3;
        }

        /**
         * Returns the next child node.
         *
         * @return The next child node.
         * @throws NoSuchElementException if there are no more child nodes.
         */
        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return _keyword;
                }
                case 1 -> {
                    index++;
                    return _condition;
                }
                case 2 -> {
                    index++;
                    return _body;
                }
                default -> throw new NoSuchElementException();
            }
        }
    }
}
