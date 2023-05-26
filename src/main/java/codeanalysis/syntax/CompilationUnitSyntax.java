package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The CompilationUnitSyntax class represents a compilation unit in the syntax tree.
 * It encapsulates an expression syntax and an end-of-file token.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class CompilationUnitSyntax extends SyntaxNode {
    private final SyntaxToken _eofToken;
    private final ExpressionSyntax _expression;

    /**
     * Creates a new instance of the CompilationUnitSyntax class with the specified expression syntax and end-of-file token.
     *
     * @param expression The expression syntax.
     * @param eofToken   The end-of-file token.
     */
    public CompilationUnitSyntax(ExpressionSyntax expression, SyntaxToken eofToken) {
        _eofToken = eofToken;
        _expression = expression;
    }

    /**
     * Retrieves the end-of-file token.
     *
     * @return The end-of-file token.
     */
    public ExpressionSyntax getExpression() {
        return _expression;
    }

    /**
     * Retrieves the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.CompilationUnit;
    }

    /**
     * Retrieves an iterator over the child nodes of the syntax node.
     *
     * @return An iterator over the child nodes.
     */
    @Override
    public Iterator<SyntaxNode> getChildren() {
        return new ChildrenIterator();
    }

    /**
     * The ChildrenIterator class represents an iterator over the child nodes of a compilation unit syntax.
     * It encapsulates an index to keep track of the current child node.
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
            return index < 2;
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
                    return _expression;
                }
                case 1 -> {
                    index++;
                    return _eofToken;
                }
                default -> throw new NoSuchElementException();
            }
        }

    }
}
