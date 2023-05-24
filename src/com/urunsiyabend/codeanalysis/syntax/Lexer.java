package com.urunsiyabend.codeanalysis.syntax;

import com.urunsiyabend.codeanalysis.DiagnosticBox;
import com.urunsiyabend.codeanalysis.TextSpan;

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
    DiagnosticBox _diagnostics = new DiagnosticBox();

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
    public DiagnosticBox diagnostics() {
        return _diagnostics;
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
        int start = _position;

        if (Character.isDigit(currentChar())) {
            while (Character.isDigit(currentChar())) {
                next();
            }
            int length = _position - start;
            String text = _text.substring(start, start + length);
            int value = 0;
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                diagnostics().reportInvalidNumber(new TextSpan(start, length), _text, Integer.class);
            }
            return new SyntaxToken(SyntaxType.NumberToken, start, text, value);
        }

        if (Character.isWhitespace(currentChar())) {
            while (Character.isWhitespace(currentChar())) {
                next();
            }
            int length = _position - start;
            String text = _text.substring(start, start + length);
            return new SyntaxToken(SyntaxType.WhiteSpaceToken, start, text, null);
        }

        if (Character.isLetter(currentChar())) {

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
                return new SyntaxToken(SyntaxType.PlusToken, start, "+", null);
            }
            case '-' -> {
                next();
                return new SyntaxToken(SyntaxType.MinusToken, start, "-", null);
            }
            case '*' -> {
                next();
                return new SyntaxToken(SyntaxType.AsteriskToken, start, "*", null);
            }
            case '/' -> {
                next();
                return new SyntaxToken(SyntaxType.SlashToken, start, "/", null);
            }
            case '(' -> {
                next();
                return new SyntaxToken(SyntaxType.OpenParenthesisToken, start, "(", null);
            }
            case ')' -> {
                next();
                return new SyntaxToken(SyntaxType.CloseParenthesisToken, getPosition(), ")", null);
            }
            case '&' -> {
                if(peek(1) == '&') {
                    next(2);
                    return new SyntaxToken(SyntaxType.DoubleAmpersandToken, start, "&&", null);
                }
            }
            case '|' -> {
                if(peek(1) == '|') {
                    next(2);
                    return new SyntaxToken(SyntaxType.DoublePipeToken, start, "||", null);
                }
            }
            case '=' -> {
                if(peek(1) == '=') {
                    next(2);
                    return new SyntaxToken(SyntaxType.EqualsEqualsToken, start, "==", null);
                }
            }
            case '!' -> {
                if(peek(1) == '=') {
                    next(2);
                    return new SyntaxToken(SyntaxType.BangEqualsToken, start, "!=", null);
                }
                else {
                    next();
                    return new SyntaxToken(SyntaxType.BangToken, start, "!", null);
                }
            }

        }
        _diagnostics.reportBadCharacter(_position, currentChar());
        next();
        return new SyntaxToken(SyntaxType.BadToken, getPosition(), String.valueOf((peek(-1))), null);
    }

}
