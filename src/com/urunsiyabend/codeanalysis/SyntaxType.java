package com.urunsiyabend.codeanalysis;

/**
 * Represents the type of a syntax token or syntax node in the code analysis system.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public enum SyntaxType {
    /* Represents a whitespace token. */
    WhiteSpaceToken,

    /* Represents a number token. */
    NumberToken,

    /* Represents a plus token. */
    PlusToken,

    /* Represents a minus token. */
    MinusToken,

    /* Represents an asterisk token. */
    AsteriskToken,

    /* Represents a slash token. */
    SlashToken,

    /* Represents a closing parenthesis token. */
    CloseParenthesisToken,

    /* Represents an opening parenthesis token. */
    OpenParenthesisToken,

    /* Represents a bad or unrecognized token. */
    BadToken,

    /* Represents an end-of-file token. */
    EOFToken,

    /* Represents a number expression. */
    NumberExpression,

    /* Represents a parenthesized expression. */
    ParenthesizedExpression,

    /* Represents a binary expression. */
    BinaryExpression
}
