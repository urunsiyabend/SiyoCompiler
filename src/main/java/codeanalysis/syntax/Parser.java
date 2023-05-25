package codeanalysis.syntax;

import codeanalysis.DiagnosticBox;

import java.util.ArrayList;

/**
 * The Parser class is responsible for parsing the input text and generating a syntax tree.
 * It uses a lexer to tokenize the input text and performs syntax analysis to construct the syntax tree.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Parser {
    private final SyntaxToken[] _tokens;
    private int _position;
    private final DiagnosticBox _diagnostics = new DiagnosticBox();

    /**
     * Initializes a new instance of the Parser class with the specified input text.
     *
     * @param text The input text to be parsed.
     */
    public Parser(String text) {
        ArrayList<SyntaxToken> tokens = new ArrayList<>();

        Lexer lexer = new Lexer(text);
        SyntaxToken token;
        do {
            token = lexer.getNextToken();
            if (token.getType() != SyntaxType.WhiteSpaceToken && token.getType() != SyntaxType.BadToken) {
                tokens.add(token);
            }
        } while (token.getType() != SyntaxType.EOFToken);

        _tokens = tokens.toArray(new SyntaxToken[0]);
        _diagnostics.addAll(lexer._diagnostics);
    }

    /**
     * Retrieves the next token without consuming it.
     *
     * @param offset The offset from the current position.
     * @return The next token.
     */
    private SyntaxToken peek(int offset) {
        int index = _position + offset;
        if (index >= _tokens.length) {
            return _tokens[_tokens.length - 1];
        }
        return _tokens[index];
    }

    /**
     * Retrieves the current token.
     *
     * @return The current token.
     */
    private SyntaxToken current() {
        return peek(0);
    }

    /**
     * Retrieves the next token and advances the position.
     *
     * @return The next token.
     */
    private SyntaxToken nextToken() {
        SyntaxToken current = current();
        _position++;
        return current;
    }

    /**
     * Retrieves diagnostic box over the diagnostics produced during parsing.
     *
     * @return An iterator over the diagnostics.
     */
    public DiagnosticBox diagnostics() {
        return _diagnostics;
    }

    /**
     * Matches the current token with the specified syntax type and advances to the next token if matched.
     * If the current token does not match the expected type, an error diagnostic is added.
     *
     * @param type The expected syntax type.
     * @return The current token if matched, or a new token of the expected type with an error diagnostic.
     */
    private SyntaxToken match(SyntaxType type) {
        if (current().type == type) {
            return nextToken();
        }
        _diagnostics.reportUnexpectedToken(current()._span, current().getType(), type);
        return new SyntaxToken(type, current().position, null, null);
    }

    /**
     * Parses the input text and generates a syntax tree.
     *
     * @return The syntax tree representing the parsed input.
     */
    public SyntaxTree parse() {
        ExpressionSyntax expression = parseExpression();
        SyntaxToken eofToken = match(SyntaxType.EOFToken);
        return new SyntaxTree(_diagnostics, expression, eofToken);
    }

    /**
     * Parses the input text and generates an expression syntax.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseExpression() {
        return parseAssignmentExpression();
    }


    /**
     * Dispatches parseBinaryExpression function by default value of parentPriority 0.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseBinaryExpression() {
        return parseBinaryExpression(0);
    }

    /**
     * Parses an assignment expression.
     *
     * @return The parsed expression syntax.
     */
    public ExpressionSyntax parseAssignmentExpression() {
        if (peek(0).getType() == SyntaxType.IdentifierToken && peek(1).getType() == SyntaxType.EqualsToken) {
            SyntaxToken identifierToken = nextToken();
            SyntaxToken operatorToken = nextToken();
            ExpressionSyntax right = parseAssignmentExpression();
            return new AssignmentExpressionSyntax(identifierToken, operatorToken, right);
        }
    return parseBinaryExpression();
    }

    /**
     * Parses a binary expression.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseBinaryExpression(int parentPriority) {
        ExpressionSyntax left;
        int unaryOperatorPriority = SyntaxPriorities.getUnaryOperatorPriority(current().getType());
        if (unaryOperatorPriority != 0 && unaryOperatorPriority >= parentPriority) {
            SyntaxToken operator = nextToken();
            ExpressionSyntax operand = parseBinaryExpression();
            left = new UnaryExpressionSyntax(operator, operand);
        }
        else {
            left = parsePrimary();
        }

        while (true) {
            int priority = SyntaxPriorities.getBinaryOperatorPriority(current().getType());
            if (priority == 0 || priority <= parentPriority)
                break;
            SyntaxToken operator = nextToken();
            ExpressionSyntax right = parseBinaryExpression(priority);
            left = new BinaryExpressionSyntax(left, operator, right);
        }

        return left;
    }

    /**
     * Parses a primary expression.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parsePrimary() {
        switch (current().getType()) {
            case OpenParenthesisToken -> {
                SyntaxToken left = nextToken();
                ExpressionSyntax exp = parseBinaryExpression();
                SyntaxToken right = match(SyntaxType.CloseParenthesisToken);

                return new ParanthesizedExpressionSyntax(left, exp, right);
            }
            case FalseKeyword, TrueKeyword -> {
                SyntaxToken keywordToken = nextToken();
                var value = keywordToken.getType() == SyntaxType.TrueKeyword;
                return new LiteralExpressionSyntax(keywordToken, value);
            }
            case IdentifierToken -> {
                SyntaxToken identifierToken = nextToken();
                return new NameExpressionSyntax(identifierToken);
            }
            default -> {
                SyntaxToken numberToken = match(SyntaxType.NumberToken);
                return new LiteralExpressionSyntax(numberToken);
            }
        }
    }
}

