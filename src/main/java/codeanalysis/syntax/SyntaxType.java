package codeanalysis.syntax;

/**
 * Represents the type of syntax token or syntax node in the code analysis system.
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

    /* Represents a literal expression. */
    LiteralExpression,

    /* Represents a parenthesized expression. */
    ParenthesizedExpression,

    /* Represents a unary expression. */
    UnaryExpression,

    /* Represents a binary expression. */
    BinaryExpression,

    /* Represents a 'true' keyword. */
    TrueKeyword,

    /* Represents a 'false' keyword. */
    FalseKeyword,

    /* Represents a identifier token. */
    IdentifierToken,

    /* Represents a bang (!) token. */
    BangToken,

    /* Represents a double ampersand (&&) token. */
    DoubleAmpersandToken,

    /* Represents a double pipe (||) token. */
    DoublePipeToken,

    /* Represents a double equals (==) token. */
    EqualsEqualsToken,

    /* Represents a bang equals (!=) token. */
    BangEqualsToken,

    /* Represents a name expression token. */
    NameExpression,

    /* Represents an assignment expression token. */
    AssignmentExpression,

    /* Represents a equals (=) token. */
    EqualsToken,

    /* Represents a compilation unit. */
    CompilationUnit,
}
