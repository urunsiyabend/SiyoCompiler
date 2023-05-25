package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The AssignmentExpressionSyntax class represents an assignment expression in the syntax tree.
 * It consists of an identifier token, an equals token, and an expression syntax representing the assigned value.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class AssignmentExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _identifierToken;
    private final SyntaxToken _equalsToken;
    private final ExpressionSyntax _expressionSyntax;

    /**
     * Creates a new instance of the AssignmentExpressionSyntax class with the specified identifier token,
     * equals token, and expression syntax.
     *
     * @param identifierToken   The identifier token.
     * @param equalsToken       The equals token.
     * @param expressionSyntax  The expression syntax representing the assigned value.
     */
    public AssignmentExpressionSyntax(SyntaxToken identifierToken, SyntaxToken equalsToken, ExpressionSyntax expressionSyntax) {
        _identifierToken = identifierToken;
        _expressionSyntax = expressionSyntax;
        _equalsToken = equalsToken;
    }

    /**
     * Retrieves the expression syntax representing the assigned value.
     *
     * @return The expression syntax.
     */
    public ExpressionSyntax getExpressionSyntax() {
        return _expressionSyntax;
    }

    /**
     * Retrieves the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.AssignmentExpression;
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
     * Retrieves the identifier token.
     *
     * @return The identifier token.
     */
    public SyntaxToken getIdentifierToken() {
        return _identifierToken;
    }

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
                    return _identifierToken;
                }
                case 1 -> {
                    index++;
                    return _equalsToken;
                }
                case 2 -> {
                    index++;
                    return _expressionSyntax;
                }
                default -> throw new NoSuchElementException();
            }
        }

    }
}
