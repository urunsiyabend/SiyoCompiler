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

    // String interpolation mode stack: each entry is a brace depth counter.
    // When inside an interpolated string and we hit {, we push a new depth=1.
    // Normal } decrements; when depth reaches 0, we resume string lexing.
    private final java.util.ArrayDeque<Integer> _interpStack = new java.util.ArrayDeque<>();
    // Track whether current interpolation is inside a triple-quote string,
    // so continuation after } knows to look for """ instead of " as closing delimiter.
    private int _tripleQuoteInterpolationDepth = 0;
    // For $name interpolation: after emitting the string-part token, we need
    // getNextToken() to read an identifier, then resume the string.
    private boolean _pendingSimpleInterpRead = false;
    private boolean _pendingSimpleInterpResume = false;
    private boolean _pendingSimpleInterpIsTripleQuote = false;

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

        // Handle $name interpolation phase 1: read the identifier token
        if (_pendingSimpleInterpRead) {
            _pendingSimpleInterpRead = false;
            _pendingSimpleInterpResume = true;
            readKeywordOrIdentifier();
            int length = _position - _start;
            String text = _text.toString(_start, _start + length);
            return new SyntaxToken(_type, _start, text, _value);
        }

        // Handle $name interpolation phase 2: resume string after identifier
        if (_pendingSimpleInterpResume) {
            _pendingSimpleInterpResume = false;
            if (_pendingSimpleInterpIsTripleQuote) {
                readTripleQuoteStringContent(false);
            } else {
                readStringContent(false);
            }
            int length = _position - _start;
            String text = _text.toString(_start, _start + length);
            return new SyntaxToken(_type, _start, text, _value);
        }

        // If inside ${expr} interpolation and we see }, check if this closes the interpolation
        if (!_interpStack.isEmpty() && currentChar() == '}') {
            int depth = _interpStack.peek() - 1;
            if (depth == 0) {
                // Closing } of interpolation — resume string lexing
                _interpStack.pop();
                next(); // consume }
                readInterpolatedStringContinuation();
                int length = _position - _start;
                String text = _text.toString(_start, _start + length);
                return new SyntaxToken(_type, _start, text, _value);
            } else {
                _interpStack.pop();
                _interpStack.push(depth);
            }
        }

        // If inside interpolation and we see {, increment brace depth
        if (!_interpStack.isEmpty() && currentChar() == '{') {
            _interpStack.push(_interpStack.pop() + 1);
        }

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
                } else if (currentChar() == '>') {
                    next();
                    _type = SyntaxType.FatArrowToken;
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
     * Handles escape sequences and string interpolation using a mode stack.
     *
     * For plain strings: emits StringToken.
     * For interpolated strings: emits InterpolatedStringStartToken, then normal code tokens
     * are lexed by getNextToken() until matching }, then readInterpolatedStringContinuation()
     * emits InterpolatedStringMidToken or InterpolatedStringEndToken.
     */
    private void readStringToken() {
        // Check for triple-quote multi-line string: """..."""
        // Only trigger if """ is followed by newline or """ (empty multi-line string)
        if (peek(1) == '"' && peek(2) == '"'
                && (peek(3) == '\n' || peek(3) == '\r' || (peek(3) == '"' && peek(4) == '"' && peek(5) == '"'))) {
            readTripleQuoteString();
            return;
        }
        // Skip opening quote
        next();
        readStringContent(true);
    }

    private void readTripleQuoteString() {
        next(); next(); next(); // skip opening """
        // Skip leading newline after opening """
        if (currentChar() == '\r') next();
        if (currentChar() == '\n') next();

        readTripleQuoteStringContent(true);
    }

    /**
     * Core triple-quote string content reader. Like readStringContent but uses """ as
     * closing delimiter and allows newlines. Supports interpolation via $name / ${expr}.
     * @param isStart true if this is the opening of a new triple-quote string
     */
    private void readTripleQuoteStringContent(boolean isStart) {
        StringBuilder sb = new StringBuilder();

        while (true) {
            char ch = currentChar();
            if (ch == '\0') {
                _diagnostics.reportUnterminatedString(new TextSpan(_start, _position - _start));
                _type = isStart ? SyntaxType.StringToken : SyntaxType.InterpolatedStringEndToken;
                _value = sb.toString();
                return;
            }
            if (ch == '"' && peek(1) == '"' && peek(2) == '"') {
                next(); next(); next(); // skip closing """

                // Strip trailing newline before closing """
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                    sb.setLength(sb.length() - 1);
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\r') {
                        sb.setLength(sb.length() - 1);
                    }
                }

                if (isStart) {
                    _type = SyntaxType.StringToken;
                } else {
                    _type = SyntaxType.InterpolatedStringEndToken;
                }
                _value = sb.toString();
                return;
            }
            if (ch == '\\') {
                next();
                switch (currentChar()) {
                    case 'n' -> { sb.append('\n'); next(); }
                    case 'r' -> { sb.append('\r'); next(); }
                    case 't' -> { sb.append('\t'); next(); }
                    case '\\' -> { sb.append('\\'); next(); }
                    case '"' -> { sb.append('"'); next(); }
                    case '0' -> { sb.append('\0'); next(); }
                    case '$' -> { sb.append('$'); next(); }
                    case '{' -> { sb.append('{'); next(); }
                    case '}' -> { sb.append('}'); next(); }
                    default -> {
                        _diagnostics.reportInvalidEscapeCharacter(new TextSpan(_position - 1, 2), currentChar());
                        next();
                    }
                }
            } else if (ch == '$') {
                char afterDollar = peek(1);
                if (afterDollar == '{') {
                    // ${expr} — expression interpolation
                    next(); next(); // consume $ and {
                    _interpStack.push(1);
                    _tripleQuoteInterpolationDepth++;
                    _type = isStart ? SyntaxType.InterpolatedStringStartToken : SyntaxType.InterpolatedStringMidToken;
                    _value = sb.toString();
                    return;
                } else if (Character.isLetter(afterDollar) || afterDollar == '_') {
                    // $name — simple variable interpolation
                    next(); // consume $
                    _pendingSimpleInterpRead = true;
                    _pendingSimpleInterpIsTripleQuote = true;
                    _type = isStart ? SyntaxType.InterpolatedStringStartToken : SyntaxType.InterpolatedStringMidToken;
                    _value = sb.toString();
                    return;
                } else {
                    sb.append('$');
                    next();
                }
            } else {
                sb.append(ch);
                next();
            }
        }
    }

    /**
     * Called when resuming string lexing after a } closes an interpolation.
     * Emits InterpolatedStringMidToken or InterpolatedStringEndToken.
     */
    private void readInterpolatedStringContinuation() {
        if (_tripleQuoteInterpolationDepth > 0) {
            _tripleQuoteInterpolationDepth--;
            readTripleQuoteStringContent(false);
        } else {
            readStringContent(false);
        }
    }

    /**
     * Core string content reader. Reads characters until closing " or $ interpolation.
     * @param isStart true if this is the opening of a new string (after the opening ")
     */
    private void readStringContent(boolean isStart) {
        StringBuilder sb = new StringBuilder();

        while (true) {
            char ch = currentChar();
            switch (ch) {
                case '\0', '\r', '\n' -> {
                    _diagnostics.reportUnterminatedString(new TextSpan(_start, _position - _start));
                    _type = isStart ? SyntaxType.StringToken : SyntaxType.InterpolatedStringEndToken;
                    _value = sb.toString();
                    return;
                }
                case '\\' -> {
                    next();
                    switch (currentChar()) {
                        case 'n' -> { sb.append('\n'); next(); }
                        case 'r' -> { sb.append('\r'); next(); }
                        case 't' -> { sb.append('\t'); next(); }
                        case '\\' -> { sb.append('\\'); next(); }
                        case '"' -> { sb.append('"'); next(); }
                        case '0' -> { sb.append('\0'); next(); }
                        case '$' -> { sb.append('$'); next(); }
                        case '{' -> { sb.append('{'); next(); }
                        case '}' -> { sb.append('}'); next(); }
                        default -> {
                            _diagnostics.reportInvalidEscapeCharacter(new TextSpan(_position - 1, 2), currentChar());
                            next();
                        }
                    }
                }
                case '$' -> {
                    char afterDollar = peek(1);
                    if (afterDollar == '{') {
                        // ${expr} — expression interpolation
                        next(); next(); // consume $ and {
                        _interpStack.push(1);
                        _type = isStart ? SyntaxType.InterpolatedStringStartToken : SyntaxType.InterpolatedStringMidToken;
                        _value = sb.toString();
                        return;
                    } else if (Character.isLetter(afterDollar) || afterDollar == '_') {
                        // $name — simple variable interpolation
                        next(); // consume $
                        _pendingSimpleInterpRead = true;
                        _pendingSimpleInterpIsTripleQuote = false;
                        _type = isStart ? SyntaxType.InterpolatedStringStartToken : SyntaxType.InterpolatedStringMidToken;
                        _value = sb.toString();
                        return;
                    } else {
                        // Plain $ character
                        sb.append('$');
                        next();
                    }
                }
                case '"' -> {
                    next(); // consume closing "
                    if (isStart) {
                        _type = SyntaxType.StringToken;
                    } else {
                        _type = SyntaxType.InterpolatedStringEndToken;
                    }
                    _value = sb.toString();
                    return;
                }
                default -> {
                    sb.append(ch);
                    next();
                }
            }
        }
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
