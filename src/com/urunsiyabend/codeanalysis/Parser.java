package com.urunsiyabend.codeanalysis;

import java.util.ArrayList;
import java.util.Iterator;

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
    private final ArrayList<String> _diagnostics = new ArrayList<>();

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
     * Retrieves an iterator over the diagnostics produced during parsing.
     *
     * @return An iterator over the diagnostics.
     */
    public Iterator<String> diagnostics() {
        return _diagnostics.iterator();
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
        _diagnostics.add(String.format("ERROR: Unexpected token: <%s>, expected <%s>", current().type, type));
        return new SyntaxToken(type, current().position, null, null);
    }

    /**
     * Parses the input text and generates a syntax tree.
     *
     * @return The syntax tree representing the parsed input.
     */
    public SyntaxTree parse() {
        ExpressionSyntax expression = parseTerm();
        SyntaxToken eofToken = match(SyntaxType.EOFToken);
        return new SyntaxTree(_diagnostics, expression, eofToken);
    }

    /**
     * Parses a term expression.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseTerm() {
        ExpressionSyntax left = parseFactor();

        while (current().type == SyntaxType.PlusToken ||
                current().type == SyntaxType.MinusToken
                ) {
            SyntaxToken operator = nextToken();
            ExpressionSyntax right = parseFactor();
            left = new BinaryExpressionSyntax(left, operator, right);
        }

        return left;
    }

    /**
     * Parses expressions.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax ParseExp() {
        return parseTerm();
    }

    /**
     * Parses a factor expression.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseFactor() {
        ExpressionSyntax left = parsePrimary();

        while (current().type == SyntaxType.AsteriskToken ||
                current().type == SyntaxType.SlashToken) {
            SyntaxToken operator = nextToken();
            ExpressionSyntax right = parsePrimary();
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
        if (current().type == SyntaxType.OpenParenthesisToken) {
            SyntaxToken left = nextToken();
            ExpressionSyntax exp = ParseExp();
            SyntaxToken right = match(SyntaxType.CloseParenthesisToken);

            return new ParanthesizedExpressionSyntax(left, exp, right);
        }
        SyntaxToken numberToken = match(SyntaxType.NumberToken);
        return new NumberExpressionSyntax(numberToken);
    }

}

