package codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a block statement in the syntax tree.
 * This class defines a block statement, which consists of an open brace token, a list of statements,
 * and a close brace token. It is used to represent statements like "if (x) { y = 1; }".
 * <p>
 * This class inherits from the StatementSyntax class and provides additional properties and methods
 * specific to block statements.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BlockStatementSyntax extends StatementSyntax {
    private final SyntaxToken _openBraceToken;
    private final ArrayList<StatementSyntax> _statements;
    private final SyntaxToken _closeBraceToken;

    /**
     * Initializes a new instance of the BlockStatementSyntax class.
     *
     * @param openBraceToken  The open brace token.
     * @param statements      The list of statements.
     * @param closeBraceToken The close brace token.
     */
    public BlockStatementSyntax (SyntaxToken openBraceToken, ArrayList<StatementSyntax> statements, SyntaxToken closeBraceToken) {
        this._openBraceToken = openBraceToken;
        this._statements = statements;
        this._closeBraceToken = closeBraceToken;
    }


    /**
     * Gets the open brace token.
     *
     * @return The open brace token of the block statement.
     */
    public SyntaxToken getOpenBraceToken() {
        return _openBraceToken;
    }

    /**
     * Gets the list of statements.
     *
     * @return The list of statements.
     */
    public ArrayList<StatementSyntax> getStatements() {
        return _statements;
    }

    /**
     * Gets the close brace token.
     *
     * @return The close brace token of the block statement.
     */
    public SyntaxToken getCloseBraceToken() {
        return _closeBraceToken;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The type of the syntax node.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.BlockStatement;
    }

    /**
     * Gets the child nodes of the syntax node.
     *
     * @return The child nodes of the syntax node.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    /**
     * An iterator over the child nodes of the syntax node.
     * This class defines an iterator over the child nodes of the syntax node.
     * It is used to iterate over the child nodes of the block statement syntax node.
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
                    return _openBraceToken;
                }
                case 1 -> {
                    index++;
                    return new IterableSyntaxNode<>(_statements); // Wrap statements in a custom iterable
                }
                case 2 -> {
                    index++;
                    return _closeBraceToken;
                }
                default -> throw new NoSuchElementException();
            }
        }
    }

    /**
     * A custom iterable over a list of statements.
     * This class defines a custom iterable over a list of statements.
     * It is used to wrap the list of statements in a custom iterable, so that the
     * BlockStatementSyntax class can return an iterator over its child nodes.
     *
     * @param <T> The type of the elements in the list of statements.
     * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
     * @author Siyabend Urun
     * @version 1.0
     */
    private static class IterableSyntaxNode<T> extends SyntaxNode implements Iterable<T> {
        private final Iterable<T> iterable;

        /**
         * Initializes a new instance of the IterableSyntaxNode class.
         *
         * @param iterable The list of statements.
         */
        public IterableSyntaxNode(Iterable<T> iterable) {
            this.iterable = iterable;
        }

        /**
         * Gets an iterator over the list of statements.
         *
         * @return An iterator over the list of statements.
         */
        @Override
        public Iterator<T> iterator() {
            return iterable.iterator();
        }

        /**
         * Gets the type of the syntax node.
         *
         * @return The type of the syntax node.
         */
        @Override
        public SyntaxType getType() {
            return SyntaxType.BlockStatement;
        }

        /**
         * Gets the child nodes of the syntax node.
         *
         * @return The child nodes of the syntax node.
         */
        @Override
        public Iterator<SyntaxNode> getChildren() {
            return new Iterator<>() {
                private final Iterator<T> iterator = iterable.iterator();

                /**
                 * Checks if there are more child nodes to iterate over.
                 *
                 * @return {@code true} if there are more child nodes, {@code false} otherwise.
                 */
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                /**
                 * Returns the next child node.
                 *
                 * @return The next child node.
                 * @throws NoSuchElementException if there are no more child nodes.
                 */
                @Override
                public SyntaxNode next() {
                    return (SyntaxNode) iterator.next();
                }
            };
        }
    }
}
