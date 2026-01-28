package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a function call expression in the syntax tree.
 * A call expression consists of an identifier and a list of arguments.
 * Example: "add(1, 2)"
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class CallExpressionSyntax extends ExpressionSyntax {
    private final SyntaxToken _identifier;
    private final SyntaxToken _openParenthesisToken;
    private final SeparatedSyntaxList<ExpressionSyntax> _arguments;
    private final SyntaxToken _closeParenthesisToken;

    /**
     * Creates a new instance of the CallExpressionSyntax class.
     *
     * @param identifier            The function name identifier token.
     * @param openParenthesisToken  The open parenthesis token.
     * @param arguments             The list of arguments.
     * @param closeParenthesisToken The close parenthesis token.
     */
    public CallExpressionSyntax(SyntaxToken identifier,
                                SyntaxToken openParenthesisToken,
                                SeparatedSyntaxList<ExpressionSyntax> arguments,
                                SyntaxToken closeParenthesisToken) {
        _identifier = identifier;
        _openParenthesisToken = openParenthesisToken;
        _arguments = arguments;
        _closeParenthesisToken = closeParenthesisToken;
    }

    /**
     * Gets the function identifier token.
     *
     * @return The identifier token.
     */
    public SyntaxToken getIdentifier() {
        return _identifier;
    }

    /**
     * Gets the open parenthesis token.
     *
     * @return The open parenthesis token.
     */
    public SyntaxToken getOpenParenthesisToken() {
        return _openParenthesisToken;
    }

    /**
     * Gets the arguments list.
     *
     * @return The separated syntax list of arguments.
     */
    public SeparatedSyntaxList<ExpressionSyntax> getArguments() {
        return _arguments;
    }

    /**
     * Gets the close parenthesis token.
     *
     * @return The close parenthesis token.
     */
    public SyntaxToken getCloseParenthesisToken() {
        return _closeParenthesisToken;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.CallExpression;
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
        private int argumentIndex;
        private boolean inArguments;

        @Override
        public boolean hasNext() {
            if (inArguments) {
                return argumentIndex < _arguments.getNodesAndSeparators().size() || index < 4;
            }
            return index < 4;
        }

        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            if (inArguments) {
                if (argumentIndex < _arguments.getNodesAndSeparators().size()) {
                    return _arguments.getNodesAndSeparators().get(argumentIndex++);
                }
                inArguments = false;
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return _identifier;
                }
                case 1 -> {
                    index++;
                    return _openParenthesisToken;
                }
                case 2 -> {
                    index++;
                    inArguments = true;
                    argumentIndex = 0;
                    if (_arguments.getNodesAndSeparators().size() > 0) {
                        return _arguments.getNodesAndSeparators().get(argumentIndex++);
                    }
                    inArguments = false;
                    return next();
                }
                case 3 -> {
                    index++;
                    return _closeParenthesisToken;
                }
                default -> throw new NoSuchElementException();
            }
        }
    }
}
