package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a parameter in a function declaration.
 * A parameter consists of an identifier token, a colon token, and a type token.
 * Example: "a: int"
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ParameterSyntax extends SyntaxNode {
    private final SyntaxToken _mutKeyword; // null if not mutable
    private final SyntaxToken _identifier;
    private final SyntaxToken _colonToken;
    private final SyntaxToken _type;

    public ParameterSyntax(SyntaxToken identifier, SyntaxToken colonToken, SyntaxToken type) {
        _mutKeyword = null;
        _identifier = identifier;
        _colonToken = colonToken;
        _type = type;
    }

    public ParameterSyntax(SyntaxToken mutKeyword, SyntaxToken identifier, SyntaxToken colonToken, SyntaxToken type) {
        _mutKeyword = mutKeyword;
        _identifier = identifier;
        _colonToken = colonToken;
        _type = type;
    }

    public boolean isMutable() { return _mutKeyword != null; }

    /**
     * Gets the identifier token.
     *
     * @return The identifier token.
     */
    public SyntaxToken getIdentifier() {
        return _identifier;
    }

    /**
     * Gets the colon token.
     *
     * @return The colon token.
     */
    public SyntaxToken getColonToken() {
        return _colonToken;
    }

    /**
     * Gets the type token.
     *
     * @return The type token.
     */
    public SyntaxToken getTypeToken() {
        return _type;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.Parameter;
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
            return index < 3;
        }

        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return _identifier;
                }
                case 1 -> {
                    index++;
                    return _colonToken;
                }
                case 2 -> {
                    index++;
                    return _type;
                }
                default -> throw new NoSuchElementException();
            }
        }
    }
}
