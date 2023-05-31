package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a variable declaration in the syntax tree.
 * This class defines a variable declaration, which consists of a keyword token, an identifier token,
 * an equals token, and an expression syntax representing the assigned value.
 * It is used to represent statements like "mut x = 1;" or "imut y = false".
 * <p>
 * This class inherits from the StatementSyntax class and provides additional properties and methods
 * specific to variable declarations.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class VariableDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _keyword;
    private final SyntaxToken _identifier;
    private final SyntaxToken _equalsToken;
    private final ExpressionSyntax _initializer;

    /**
     * Initializes a new instance of the VariableDeclarationSyntax class.
     *
     * @param keyword     The keyword token.
     * @param identifier  The identifier token.
     * @param equalsToken The equals token.
     * @param initializer The initializer expression.
     */
    public VariableDeclarationSyntax(SyntaxToken keyword, SyntaxToken identifier, SyntaxToken equalsToken, ExpressionSyntax initializer) {
        _keyword = keyword;
        _identifier = identifier;
        _equalsToken = equalsToken;
        _initializer = initializer;
    }

    /**
     * Gets the keyword token.
     *
     * @return The keyword token of the variable declaration.
     */
    public SyntaxToken getKeyword() {
        return _keyword;
    }

    /**
     * Gets the identifier token.
     *
     * @return The identifier token of the variable declaration.
     */
    public SyntaxToken getIdentifier() {
        return _identifier;
    }

    /**
     * Gets the equals token.
     *
     * @return The equals token of the variable declaration.
     */
    public SyntaxToken getEqualsToken() {
        return _equalsToken;
    }

    /**
     * Gets the initializer expression.
     *
     * @return The initializer expression of the variable declaration.
     */
    public ExpressionSyntax getInitializer() {
        return _initializer;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The type of the syntax node.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.VariableDeclaration;
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
            return index < 4;
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
                    return _identifier;
                }
                case 2 -> {
                    index++;
                    return _equalsToken;
                }
                case 3 -> {
                    index++;
                    return _initializer;
                }
                default -> throw new NoSuchElementException();
            }
        }

    }
}
