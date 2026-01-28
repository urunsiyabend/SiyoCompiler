package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a type clause in a function declaration.
 * A type clause consists of an arrow token and a type identifier.
 * Example: "-> int"
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class TypeClauseSyntax extends SyntaxNode {
    private final SyntaxToken _arrowToken;
    private final SyntaxToken _identifier;

    /**
     * Creates a new instance of the TypeClauseSyntax class.
     *
     * @param arrowToken The arrow token.
     * @param identifier The type identifier token.
     */
    public TypeClauseSyntax(SyntaxToken arrowToken, SyntaxToken identifier) {
        _arrowToken = arrowToken;
        _identifier = identifier;
    }

    /**
     * Gets the arrow token.
     *
     * @return The arrow token.
     */
    public SyntaxToken getArrowToken() {
        return _arrowToken;
    }

    /**
     * Gets the type identifier token.
     *
     * @return The type identifier token.
     */
    public SyntaxToken getIdentifier() {
        return _identifier;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.TypeClause;
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
                    return _arrowToken;
                }
                case 1 -> {
                    index++;
                    return _identifier;
                }
                default -> throw new NoSuchElementException();
            }
        }
    }
}
