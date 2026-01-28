package codeanalysis.syntax;

import codeanalysis.DiagnosticBox;
import codeanalysis.text.SourceText;

import java.util.ArrayList;
import java.util.List;

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
            case FnKeyword -> parseFunctionDeclaration();
            case ReturnKeyword -> parseReturnStatement();
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
            SyntaxToken startToken = current();

            StatementSyntax statement = parseStatement();
            statements.add(statement);

            if (current() == startToken) {
                nextToken();
            }
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
     * Parses a function declaration.
     * A function declaration consists of the fn keyword, identifier, parameters, optional type clause, and body.
     *
     * @return The parsed function declaration syntax.
     */
    private StatementSyntax parseFunctionDeclaration() {
        SyntaxToken fnKeyword = match(SyntaxType.FnKeyword);
        SyntaxToken identifier = match(SyntaxType.IdentifierToken);
        SyntaxToken openParenthesis = match(SyntaxType.OpenParenthesisToken);
        SeparatedSyntaxList<ParameterSyntax> parameters = parseParameterList();
        SyntaxToken closeParenthesis = match(SyntaxType.CloseParenthesisToken);
        TypeClauseSyntax typeClause = parseOptionalTypeClause();
        BlockStatementSyntax body = parseBlockStatement();
        return new FunctionDeclarationSyntax(fnKeyword, identifier, openParenthesis, parameters, closeParenthesis, typeClause, body);
    }

    /**
     * Parses a comma-separated list of parameters.
     *
     * @return The separated syntax list of parameters.
     */
    private SeparatedSyntaxList<ParameterSyntax> parseParameterList() {
        List<SyntaxNode> nodesAndSeparators = new ArrayList<>();

        while (current().getType() != SyntaxType.CloseParenthesisToken &&
               current().getType() != SyntaxType.EOFToken) {
            ParameterSyntax parameter = parseParameter();
            nodesAndSeparators.add(parameter);

            if (current().getType() != SyntaxType.CloseParenthesisToken) {
                SyntaxToken comma = match(SyntaxType.CommaToken);
                nodesAndSeparators.add(comma);
            }
        }

        return new SeparatedSyntaxList<>(nodesAndSeparators);
    }

    /**
     * Parses a single parameter.
     * A parameter consists of an identifier, a colon, and a type.
     *
     * @return The parsed parameter syntax.
     */
    private ParameterSyntax parseParameter() {
        SyntaxToken identifier = match(SyntaxType.IdentifierToken);
        SyntaxToken colon = match(SyntaxType.ColonToken);
        SyntaxToken type = match(SyntaxType.IdentifierToken);
        return new ParameterSyntax(identifier, colon, type);
    }

    /**
     * Parses an optional type clause.
     * A type clause consists of an arrow token and a type identifier.
     *
     * @return The parsed type clause syntax, or null if no type clause is present.
     */
    private TypeClauseSyntax parseOptionalTypeClause() {
        if (current().getType() != SyntaxType.ArrowToken) {
            return null;
        }
        SyntaxToken arrowToken = match(SyntaxType.ArrowToken);
        SyntaxToken identifier = match(SyntaxType.IdentifierToken);
        return new TypeClauseSyntax(arrowToken, identifier);
    }

    /**
     * Parses a return statement.
     * A return statement consists of the return keyword and an optional expression.
     *
     * @return The parsed return statement syntax.
     */
    private StatementSyntax parseReturnStatement() {
        SyntaxToken returnKeyword = match(SyntaxType.ReturnKeyword);
        ExpressionSyntax expression = null;

        // Check if there's an expression following the return keyword
        // Don't parse expression if we're at end of statement (closing brace or EOF)
        if (current().getType() != SyntaxType.CloseBraceToken &&
            current().getType() != SyntaxType.EOFToken) {
            expression = parseExpression();
        }

        return new ReturnStatementSyntax(returnKeyword, expression);
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
            case IdentifierToken -> {
                if (peek(1).getType() == SyntaxType.OpenParenthesisToken) {
                    yield parseCallExpression();
                }
                yield parseNameExpression();
            }
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

    /**
     * Parses a call expression.
     * A call expression consists of an identifier followed by arguments in parentheses.
     *
     * @return The parsed call expression syntax.
     */
    private ExpressionSyntax parseCallExpression() {
        SyntaxToken identifier = match(SyntaxType.IdentifierToken);
        SyntaxToken openParenthesis = match(SyntaxType.OpenParenthesisToken);
        SeparatedSyntaxList<ExpressionSyntax> arguments = parseArguments();
        SyntaxToken closeParenthesis = match(SyntaxType.CloseParenthesisToken);
        return new CallExpressionSyntax(identifier, openParenthesis, arguments, closeParenthesis);
    }

    /**
     * Parses a comma-separated list of arguments.
     *
     * @return The separated syntax list of arguments.
     */
    private SeparatedSyntaxList<ExpressionSyntax> parseArguments() {
        List<SyntaxNode> nodesAndSeparators = new ArrayList<>();

        while (current().getType() != SyntaxType.CloseParenthesisToken &&
               current().getType() != SyntaxType.EOFToken) {
            ExpressionSyntax expression = parseExpression();
            nodesAndSeparators.add(expression);

            if (current().getType() != SyntaxType.CloseParenthesisToken) {
                SyntaxToken comma = match(SyntaxType.CommaToken);
                nodesAndSeparators.add(comma);
            }
        }

        return new SeparatedSyntaxList<>(nodesAndSeparators);
    }
}

