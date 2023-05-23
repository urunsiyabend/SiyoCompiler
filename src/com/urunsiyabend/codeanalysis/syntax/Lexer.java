package com.urunsiyabend.codeanalysis.syntax;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * The {@code Lexer} class is responsible for lexical analysis of a given text, producing tokens that represent different elements of the text.
 * It scans the text character by character and generates tokens based on specific rules.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Lexer {
    private final String _text;
    private int _position;
    ArrayList<String> _diagnostics = new ArrayList<>();

    /**
     * Initializes a new instance of the {@code Lexer} class with the specified text to analyze.
     *
     * @param text The text to analyze.
     */
    public Lexer(String text) {
        this._text = text;
    }

    /**
     * Gets the current position of the lexer in the text.
     *
     * @return The current position of the lexer.
     */
    public int getPosition() {
        return _position;
    }

    /**
     * Gets the current character being analyzed by the lexer.
     *
     * @return The current character being analyzed.
     */
    public char currentChar() {
        return peek(0);
    }

    private char peek(int offset) {
        int index = _position + offset;
        if (index >= _text.length()) {
            return '\0';
        }
        return _text.charAt(index);
    }

    /**
     * Gets an iterator over the diagnostic messages generated during lexical analysis.
     *
     * @return An iterator over the diagnostic messages.
     */
    public Iterator<String> diagnostics() {
        return _diagnostics.iterator();
    }

    /**
     * Advances the lexer to the next character in the text.
     */
    private void next() {
        next(1);
    }

    /**
     * Advances the lexer to the character in the text according to the offset.
     *
     * @param offset The amount of change that cursor will make.
     */
    private void next(int offset) {
        _position += offset;
    }

    /**
     * Retrieves the next token from the text being analyzed.
     * This method scans the text character by character and generates tokens based on specific rules.
     *
     * @return The next token in the text.
     */
    public SyntaxToken getNextToken() {
        if (_position >= _text.length()) {
            return new SyntaxToken(SyntaxType.EOFToken, _position, "\0", null);
        }

        if (Character.isDigit(currentChar())) {
            int start = _position;
            while (Character.isDigit(currentChar())) {
                next();
            }
            int length = _position - start;
            String text = _text.substring(start, start + length);
            int value = 0;
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                _diagnostics.add(String.format("The expression %s could not be represented as Int32", text));
            }
            return new SyntaxToken(SyntaxType.NumberToken, start, text, value);
        }

        if (Character.isWhitespace(currentChar())) {
            int start = _position;
            while (Character.isWhitespace(currentChar())) {
                next();
            }
            int length = _position - start;
            String text = _text.substring(start, start + length);
            return new SyntaxToken(SyntaxType.WhiteSpaceToken, start, text, null);
        }

        if (Character.isLetter(currentChar())) {
            int start = _position;

            while(Character.isLetter(currentChar())) {
                next();
            }

            int length = _position - start;
            String text = _text.substring(start, start + length);
            SyntaxType type = SyntaxRules.getKeywordType(text);
            return new SyntaxToken(type, start, text, null);
        }

        switch (currentChar()) {
            case '+' -> {
                next();
                return new SyntaxToken(SyntaxType.PlusToken, getPosition(), "+", null);
            }
            case '-' -> {
                next();
                return new SyntaxToken(SyntaxType.MinusToken, getPosition(), "-", null);
            }
            case '*' -> {
                next();
                return new SyntaxToken(SyntaxType.AsteriskToken, getPosition(), "*", null);
            }
            case '/' -> {
                next();
                return new SyntaxToken(SyntaxType.SlashToken, getPosition(), "/", null);
            }
            case '(' -> {
                next();
                return new SyntaxToken(SyntaxType.OpenParenthesisToken, getPosition(), "(", null);
            }
            case ')' -> {
                next();
                return new SyntaxToken(SyntaxType.CloseParenthesisToken, getPosition(), ")", null);
            }
            case '&' -> {
                if(peek(1) == '&') {
                    next(2);
                    return new SyntaxToken(SyntaxType.DoubleAmpersandToken, getPosition(), "&&", null);
                }
            }
            case '|' -> {
                if(peek(1) == '|') {
                    next(2);
                    return new SyntaxToken(SyntaxType.DoublePipeToken, getPosition(), "||", null);
                }
            }
            case '=' -> {
                if(peek(1) == '=') {
                    next(2);
                    return new SyntaxToken(SyntaxType.EqualsEqualsToken, getPosition(), "==", null);
                }
            }
            case '!' -> {
                if(peek(1) == '=') {
                    next(2);
                    return new SyntaxToken(SyntaxType.BangEqualsToken, getPosition(), "!=", null);
                }
                else {
                    next();
                    return new SyntaxToken(SyntaxType.BangToken, getPosition(), "!", null);
                }
            }

        }
        _diagnostics.add(String.format("ERROR: Bad character input: %c", currentChar()));
        next();
        return new SyntaxToken(SyntaxType.BadToken, getPosition(), String.valueOf(currentChar()), null);
    }

}
