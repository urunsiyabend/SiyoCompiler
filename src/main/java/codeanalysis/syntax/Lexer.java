package codeanalysis.syntax;

import codeanalysis.DiagnosticBox;
import codeanalysis.text.SourceText;
import codeanalysis.text.TextSpan;

/**
 * The {@code Lexer} class is responsible for lexical analysis of a given text, producing tokens that represent different elements of the text.
 * It scans the text character by character and generates tokens based on specific rules.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class Lexer {
    private final SourceText _text;
    DiagnosticBox _diagnostics = new DiagnosticBox();
    private int _position;
    private int _start;
    private SyntaxType _type;
    private Object _value;

    /**
     * Initializes a new instance of the {@code Lexer} class with the specified text to analyze.
     *
     * @param text The text to analyze.
     */
    public Lexer(SourceText text) {
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
        _start = _position;
        _type = SyntaxType.BadToken;
        _value = null;

        switch (currentChar()) {
            case '\0' -> {
                _type = SyntaxType.EOFToken;
            }
            case '+' -> {
                next();
                if (currentChar() == '=') { next(); _type = SyntaxType.PlusEqualsToken; }
                else { _type = SyntaxType.PlusToken; }
            }
            case '-' -> {
                next();
                if (currentChar() == '>') { next(); _type = SyntaxType.ArrowToken; }
                else if (currentChar() == '=') { next(); _type = SyntaxType.MinusEqualsToken; }
                else { _type = SyntaxType.MinusToken; }
            }
            case '*' -> {
                next();
                if (currentChar() == '=') { next(); _type = SyntaxType.AsteriskEqualsToken; }
                else { _type = SyntaxType.AsteriskToken; }
            }
            case '/' -> {
                next();
                if (currentChar() == '/') {
                    while (currentChar() != '\n' && currentChar() != '\r' && currentChar() != '\0') {
                        next();
                    }
                    _type = SyntaxType.WhiteSpaceToken;
                } else if (currentChar() == '=') { next(); _type = SyntaxType.SlashEqualsToken; }
                else { _type = SyntaxType.SlashToken; }
            }
            case '%' -> {
                next();
                _type = SyntaxType.PercentToken;
            }
            case '(' -> {
                next();
                _type = SyntaxType.OpenParenthesisToken;
            }
            case ')' -> {
                next();
                _type = SyntaxType.CloseParenthesisToken;
            }
            case '{' -> {
                next();
                _type = SyntaxType.OpenBraceToken;
            }
            case '}' -> {
                next();
                _type = SyntaxType.CloseBraceToken;
            }
            case '[' -> {
                next();
                _type = SyntaxType.OpenBracketToken;
            }
            case ']' -> {
                next();
                _type = SyntaxType.CloseBracketToken;
            }
            case '.' -> {
                next();
                if (currentChar() == '.') { next(); _type = SyntaxType.DoubleDotToken; }
                else { _type = SyntaxType.DotToken; }
            }
            case '~' -> {
                next();
                _type = SyntaxType.TildeToken;
            }
            case '^' -> {
                next();
                _type = SyntaxType.CaretToken;
            }
            case ':' -> {
                next();
                _type = SyntaxType.ColonToken;
            }
            case ',' -> {
                next();
                _type = SyntaxType.CommaToken;
            }
            case '&' -> {
                next();
                if (currentChar() == '&') {
                    next();
                    _type = SyntaxType.DoubleAmpersandToken;
                }
                else {
                    _type = SyntaxType.AmpersandToken;
                }
            }
            case '|' -> {
                next();
                if (currentChar() == '|') {
                    next();
                    _type = SyntaxType.DoublePipeToken;
                }
                else {
                    _type = SyntaxType.PipeToken;
                }
            }
            case '=' -> {
                next();
                if (currentChar() == '=') {
                    next();
                    _type = SyntaxType.EqualsEqualsToken;
                } else {
                    _type = SyntaxType.EqualsToken;
                }
            }
            case '!' -> {
                next();
                if (currentChar() == '=') {
                    next();
                    _type = SyntaxType.BangEqualsToken;
                } else {
                    _type = SyntaxType.BangToken;
                }
            }
            case '<' -> {
                next();
                if (currentChar() == '=') {
                    next();
                    _type = SyntaxType.LessOrEqualsToken;
                } else if (currentChar() == '<') {
                    next();
                    _type = SyntaxType.DoubleLessToken;
                }
                else {
                    _type = SyntaxType.LessToken;
                }
            }
            case '>' -> {
                next();
                if (currentChar() == '=') {
                    next();
                    _type = SyntaxType.GreaterOrEqualsToken;
                } else if (currentChar() == '>') {
                    next();
                    _type = SyntaxType.DoubleGreaterToken;
                }
                else {
                    _type = SyntaxType.GreaterToken;
                }
            }
            case '"' -> readStringToken();
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> readNumberToken();
            case ' ', '\t', '\n', '\r' -> readWhitespaceToken();
            default -> {
                if (Character.isLetter(currentChar()) || currentChar() == '_') {
                    readKeywordOrIdentifier();
                } else if (Character.isWhitespace(currentChar())) {
                    readWhitespaceToken();
                } else {
                    _diagnostics.reportBadCharacter(_position, currentChar());
                    next();
                }
            }
        }

        int length = _position - _start;
        String text = SyntaxRules.getTextData(_type);
        if (text == null) {
            text = _text.toString(_start, _start + length);
        }
        return new SyntaxToken(_type, _start, text, _value);
    }

    /**
     * Reads a string token from the text being analyzed and moves the cursor.
     * Handles escape sequences: \n, \t, \\, \", \0.
     */
    private void readStringToken() {
        // Skip opening quote
        next();

        StringBuilder sb = new StringBuilder();
        boolean done = false;

        while (!done) {
            switch (currentChar()) {
                case '\0', '\r', '\n' -> {
                    _diagnostics.reportUnterminatedString(new TextSpan(_start, _position - _start));
                    done = true;
                }
                case '\\' -> {
                    next();
                    switch (currentChar()) {
                        case 'n' -> { sb.append('\n'); next(); }
                        case 't' -> { sb.append('\t'); next(); }
                        case '\\' -> { sb.append('\\'); next(); }
                        case '"' -> { sb.append('"'); next(); }
                        case '0' -> { sb.append('\0'); next(); }
                        default -> {
                            _diagnostics.reportInvalidEscapeCharacter(new TextSpan(_position - 1, 2), currentChar());
                            next();
                        }
                    }
                }
                case '"' -> {
                    next();
                    done = true;
                }
                default -> {
                    sb.append(currentChar());
                    next();
                }
            }
        }

        _type = SyntaxType.StringToken;
        _value = sb.toString();
    }

    /**
     * Reads the number token from the text being analyzed and moves the cursor. If the number is invalid, it reports an error.
     */
    private void readNumberToken() {
        while (Character.isDigit(currentChar())) {
            next();
        }

        // Check for decimal point (float literal)
        if (currentChar() == '.' && Character.isDigit(peek(1))) {
            next(); // consume '.'
            while (Character.isDigit(currentChar())) {
                next();
            }
            int length = _position - _start;
            String text = _text.toString(_start, _start + length);
            double value = 0.0;
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                _diagnostics.reportInvalidNumber(new TextSpan(_start, length), text, Integer.class);
            }
            _value = value;
            _type = SyntaxType.FloatToken;
            return;
        }

        int length = _position - _start;
        String text = _text.toString(_start, _start + length);
        int value = 0;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            _diagnostics.reportInvalidNumber(new TextSpan(_start, length), text, Integer.class);
        }
        _value = value;
        _type = SyntaxType.NumberToken;
    }

    /**
     * Reads the whitespace token from the text being analyzed and moves the cursor.
     */
    private void readWhitespaceToken() {
        while (Character.isWhitespace(currentChar())) {
            next();
        }
        _type = SyntaxType.WhiteSpaceToken;
    }

    /**
     * Reads the keyword or identifier token from the text being analyzed and moves the cursor.
     */
    private void readKeywordOrIdentifier() {
        while(Character.isLetterOrDigit(currentChar()) || currentChar() == '_') {
            next();
        }

        int length = _position - _start;
        String text = _text.toString(_start, _start + length);
        _type = SyntaxRules.getKeywordType(text);
    }
}
