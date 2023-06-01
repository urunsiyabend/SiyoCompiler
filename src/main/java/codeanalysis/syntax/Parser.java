package codeanalysis.syntax;

import codeanalysis.DiagnosticBox;
import codeanalysis.text.SourceText;

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

    private final SourceText _text;
    /**
     * Initializes a new instance of the Parser class with the specified input text.
     *
     * @param text The input text to be parsed.
     */
    public Parser(SourceText text) {
        ArrayList<SyntaxToken> tokens = new ArrayList<>();

        Lexer lexer = new Lexer(text);
        SyntaxToken token;
        do {
            token = lexer.getNextToken();
            if (token.getType() != SyntaxType.WhiteSpaceToken && token.getType() != SyntaxType.BadToken) {
                tokens.add(token);
            }
        } while (token.getType() != SyntaxType.EOFToken);

        _text = text;
        _tokens = tokens.toArray(new SyntaxToken[0]);
        _diagnostics.addAll(lexer._diagnostics);
    }

    /**
     * Gets the diagnostic box over the diagnostics produced during parsing.
     *
     * @return The diagnostic box.
     */
    public DiagnosticBox getDiagnostics() {
        return _diagnostics;
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
    public CompilationUnitSyntax parseCompilationUnit() {
        StatementSyntax statement = parseStatement();
        SyntaxToken eofToken = match(SyntaxType.EOFToken);
        return new CompilationUnitSyntax(statement, eofToken);
    }

    /**
     * Parses a statement.
     * A statement can be either a block statement, variable declaration, if statement or an expression statement.
     *
     * @return The parsed statement syntax.
     */
    private StatementSyntax parseStatement() {
        return switch (current().getType()) {
            case OpenBraceToken -> parseBlockStatement();
            case ImmutableKeyword, MutableKeyword -> parseVariableDeclaration();
            case IfKeyword -> parseIfStatement();
            case WhileKeyword -> parseWhileStatement();
            case ForKeyword -> parseForStatement();
            default -> parseExpressionStatement();
        };
    }

    /**
     * Parses a block statement.
     * A block statement is a sequence of statements enclosed in curly braces.
     * The block statement is used to group statements together.
     *
     * @return The parsed block statement syntax.
     */
    private BlockStatementSyntax parseBlockStatement() {
        ArrayList<StatementSyntax> statements = new ArrayList<>();

        SyntaxToken openBraceToken = match(SyntaxType.OpenBraceToken);

        while (current().type != SyntaxType.EOFToken && current().type != SyntaxType.CloseBraceToken) {
            StatementSyntax statement = parseStatement();
            statements.add(statement);
        }

        SyntaxToken closeBraceToken = match(SyntaxType.CloseBraceToken);

        return new BlockStatementSyntax(openBraceToken, statements, closeBraceToken);
    }

    /**
     * Parses an expression statement.
     * An expression statement is a statement that consists of an expression.
     *
     * @return The parsed expression statement syntax.
     */
    private ExpressionStatementSyntax parseExpressionStatement() {
        return new ExpressionStatementSyntax(parseExpression());
    }

    /**
     * Parses an expression.
     * An expression can be either a binary expression, unary expression, literal expression or a parenthesized expression.
     *
     * @return The parsed expression syntax.
     */
    private VariableDeclarationSyntax parseVariableDeclaration() {
        SyntaxType expectedKeyword = current().getType() == SyntaxType.ImmutableKeyword ? SyntaxType.ImmutableKeyword : SyntaxType.MutableKeyword;
        SyntaxToken keyword = match(expectedKeyword);
        SyntaxToken identifier = match(SyntaxType.IdentifierToken);
        SyntaxToken equals = match(SyntaxType.EqualsToken);
        ExpressionSyntax initializer = parseExpression();
        return new VariableDeclarationSyntax(keyword, identifier, equals, initializer);
    }

    /**
     * Parses an if statement.
     * An if statement is a statement that consists of a condition, a then statement and an optional else clause.
     *
     * @return The parsed if statement syntax.
     */
    private StatementSyntax parseIfStatement() {
        SyntaxToken keyword = match(SyntaxType.IfKeyword);
        ExpressionSyntax condition = parseExpression();
        StatementSyntax thenStatement = parseStatement();
        ElseClauseSyntax elseClause = parseElseClause();
        return new IfStatementSyntax(keyword, condition, thenStatement, elseClause);
    }

    /**
     * Parses a while statement.
     * A while statement is a statement that consists of a condition and a body statement.
     * The body statement is executed as long as the condition evaluates to true.
     *
     * @return The parsed while statement syntax.
     */
    private StatementSyntax parseWhileStatement() {
        SyntaxToken keyword = match(SyntaxType.WhileKeyword);
        ExpressionSyntax condition = parseExpression();
        StatementSyntax body = parseStatement();
        return new WhileStatementSyntax(keyword, condition, body);
    }

    /**
     * Parses a for statement.
     * A for statement consists of an initializer, a condition, an increment expression, and a body statement.
     * The initializer is executed once at the beginning.
     * The condition is checked before each iteration, and if false, the loop is terminated.
     * The increment expression is executed at the end of each iteration.
     *
     * @return The parsed for statement syntax.
     */
    private StatementSyntax parseForStatement() {
        SyntaxToken forKeyword = match(SyntaxType.ForKeyword);

        StatementSyntax initializer = parseStatement();

        ExpressionSyntax condition = parseExpression();

        ExpressionSyntax iterator = parseExpression();

        StatementSyntax body = parseStatement();

        return new ForStatementSyntax(forKeyword, initializer, condition, iterator, body);
    }


    /**
     * Parses an else clause.
     * An else clause is a statement that consists of an else keyword and a statement.
     *
     * @return The parsed else clause syntax.
     */
    private ElseClauseSyntax parseElseClause() {
        if (current().getType() != SyntaxType.ElseKeyword)
            return null;
        SyntaxToken keyword = nextToken();
        StatementSyntax statement = parseStatement();
        return new ElseClauseSyntax(keyword, statement);
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
            ExpressionSyntax operand = parseBinaryExpression(unaryOperatorPriority);
            left = new UnaryExpressionSyntax(operator, operand);
        }
        else {
            left = parsePrimary();
        }

        while (SyntaxPriorities.getBinaryOperatorPriority(current().getType()) > parentPriority) {
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
        return switch (current().getType()) {
            case OpenParenthesisToken -> parseParenthesizedExpression();
            case FalseKeyword, TrueKeyword -> parseBooleanLiteral();
            case NumberToken -> parseNumberLiteral();
            default -> parseNameExpression();
        };
    }

    /**
     * Parses a parenthesized expression.
     * ParenthesizedExpressionSyntax has a left and right parenthesis token, and an expression.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseParenthesizedExpression() {
        SyntaxToken left = match(SyntaxType.OpenParenthesisToken);
        ExpressionSyntax exp = parseExpression();
        SyntaxToken right = match(SyntaxType.CloseParenthesisToken);
        return new ParanthesizedExpressionSyntax(left, exp, right);
    }

    /**
     * Parses a boolean literal.
     * LiteralExpressionSyntax has a keyword token and a value.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseBooleanLiteral() {
        boolean isTrue = current().getType() == SyntaxType.TrueKeyword;
        SyntaxToken keywordToken = isTrue ? match(SyntaxType.TrueKeyword) : match(SyntaxType.FalseKeyword);
        return new LiteralExpressionSyntax(keywordToken, isTrue);
    }

    /**
     * Parses a number literal.
     * LiteralExpressionSyntax has a number token and a value.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseNumberLiteral() {
        SyntaxToken numberToken = match(SyntaxType.NumberToken);
        return new LiteralExpressionSyntax(numberToken);
    }

    /**
     * Parses a name expression.
     * NameExpressionSyntax has an identifier token.
     *
     * @return The parsed expression syntax.
     */
    private ExpressionSyntax parseNameExpression() {
        SyntaxToken identifierToken = match(SyntaxType.IdentifierToken);
        return new NameExpressionSyntax(identifierToken);
    }
}

