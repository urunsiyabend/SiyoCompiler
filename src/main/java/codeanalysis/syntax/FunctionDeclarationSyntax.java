package codeanalysis.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a function declaration in the syntax tree.
 * A function declaration consists of the fn keyword, identifier, parameters, optional type clause, and body.
 * Example: "fn add(a: int, b: int) -> int { return a + b }"
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class FunctionDeclarationSyntax extends StatementSyntax {
    private final SyntaxToken _fnKeyword;
    private final SyntaxToken _identifier;
    private final SyntaxToken _openParenthesisToken;
    private final SeparatedSyntaxList<ParameterSyntax> _parameters;
    private final SyntaxToken _closeParenthesisToken;
    private final TypeClauseSyntax _typeClause;
    private final BlockStatementSyntax _body;

    /**
     * Creates a new instance of the FunctionDeclarationSyntax class.
     *
     * @param fnKeyword             The fn keyword token.
     * @param identifier            The function name identifier token.
     * @param openParenthesisToken  The open parenthesis token.
     * @param parameters            The parameters list.
     * @param closeParenthesisToken The close parenthesis token.
     * @param typeClause            The optional return type clause (can be null).
     * @param body                  The function body block.
     */
    public FunctionDeclarationSyntax(SyntaxToken fnKeyword, SyntaxToken identifier,
                                     SyntaxToken openParenthesisToken,
                                     SeparatedSyntaxList<ParameterSyntax> parameters,
                                     SyntaxToken closeParenthesisToken,
                                     TypeClauseSyntax typeClause,
                                     BlockStatementSyntax body) {
        _fnKeyword = fnKeyword;
        _identifier = identifier;
        _openParenthesisToken = openParenthesisToken;
        _parameters = parameters;
        _closeParenthesisToken = closeParenthesisToken;
        _typeClause = typeClause;
        _body = body;
    }

    /**
     * Gets the fn keyword token.
     *
     * @return The fn keyword token.
     */
    public SyntaxToken getFnKeyword() {
        return _fnKeyword;
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
     * Gets the parameters list.
     *
     * @return The separated syntax list of parameters.
     */
    public SeparatedSyntaxList<ParameterSyntax> getParameters() {
        return _parameters;
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
     * Gets the optional type clause.
     *
     * @return The type clause, or null if the function has no return type.
     */
    public TypeClauseSyntax getTypeClause() {
        return _typeClause;
    }

    /**
     * Gets the function body.
     *
     * @return The body block statement.
     */
    public BlockStatementSyntax getBody() {
        return _body;
    }

    /**
     * Gets the type of the syntax node.
     *
     * @return The syntax type.
     */
    @Override
    public SyntaxType getType() {
        return SyntaxType.FunctionDeclaration;
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
        private int parameterIndex;
        private boolean inParameters;

        @Override
        public boolean hasNext() {
            if (inParameters) {
                return parameterIndex < _parameters.getNodesAndSeparators().size() || hasMoreAfterParameters();
            }
            return index < getMaxIndex();
        }

        private int getMaxIndex() {
            return _typeClause == null ? 5 : 6;
        }

        private boolean hasMoreAfterParameters() {
            return _typeClause == null ? index < 5 : index < 6;
        }

        @Override
        public SyntaxNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            if (inParameters) {
                if (parameterIndex < _parameters.getNodesAndSeparators().size()) {
                    return _parameters.getNodesAndSeparators().get(parameterIndex++);
                }
                inParameters = false;
            }

            switch (index) {
                case 0 -> {
                    index++;
                    return _fnKeyword;
                }
                case 1 -> {
                    index++;
                    return _identifier;
                }
                case 2 -> {
                    index++;
                    return _openParenthesisToken;
                }
                case 3 -> {
                    index++;
                    inParameters = true;
                    parameterIndex = 0;
                    if (_parameters.getNodesAndSeparators().size() > 0) {
                        return _parameters.getNodesAndSeparators().get(parameterIndex++);
                    }
                    inParameters = false;
                    return next();
                }
                case 4 -> {
                    index++;
                    return _closeParenthesisToken;
                }
                case 5 -> {
                    index++;
                    if (_typeClause != null) {
                        return _typeClause;
                    }
                    return _body;
                }
                case 6 -> {
                    index++;
                    return _body;
                }
                default -> throw new NoSuchElementException();
            }
        }
    }
}
