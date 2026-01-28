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

    /* Represents a percent (%) token. */
    PercentToken,

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

    /* Represents a block statement. */
    BlockStatement,

    /* Represents a expression statement. */
    ExpressionStatement,

    /* Represents a open brace token. */
    OpenBraceToken,

    /* Represents a close brace token. */
    CloseBraceToken,

    /* Represents a variable declaration. */
    VariableDeclaration,

    /* Represents a "mut" keyword. */
    MutableKeyword,

    /* Represents an "imut" keyword. */
    ImmutableKeyword,

    /* Represents a less (<) keyword. */
    LessToken,

    /* Represents a less or equals (<=) keyword. */
    LessOrEqualsToken,

    /* Represents a greater (>) keyword. */
    GreaterToken,

    /* Represents a greater or equals (>=) keyword. */
    GreaterOrEqualsToken,

    /* Represents an if statement */
    IfStatement,

    /* Represents an else clause */
    ElseClause,

    /* Represents an if (if) keyword */
    IfKeyword,

    /* Represents an else (else) keyword */
    ElseKeyword,

    /* Represents a while (while) keyword */
    WhileKeyword,

    /* Represents a while statement */
    WhileStatement,

    /* Represents a for (for) keyword */
    ForKeyword,

    /* Represents a for statement */
    ForStatement,

    /* Represents a tilde (~) token. */
    TildeToken,

    /* Represents a ampersand (&) token. */
    AmpersandToken,

    /* Represents a pipe (|) token. */
    PipeToken,

    /* Represents a double less (<<) token. */
    DoubleLessToken,

    /* Represents a double greater (>>) token. */
    DoubleGreaterToken,

    /* Represents a caret (^) token. */
    CaretToken,

    /* Represents a colon (:) token. */
    ColonToken,

    /* Represents a comma (,) token. */
    CommaToken,

    /* Represents an arrow (->) token. */
    ArrowToken,

    /* Represents a 'fn' keyword. */
    FnKeyword,

    /* Represents a 'return' keyword. */
    ReturnKeyword,

    /* Represents a parameter in function declaration. */
    Parameter,

    /* Represents a type clause (-> Type). */
    TypeClause,

    /* Represents a function declaration. */
    FunctionDeclaration,

    /* Represents a return statement. */
    ReturnStatement,

    /* Represents a call expression. */
    CallExpression,
    }
