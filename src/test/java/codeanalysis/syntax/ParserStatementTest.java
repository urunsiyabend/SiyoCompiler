package codeanalysis.syntax;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ParserStatementTest class contains tests for parsing statement syntax.
 * It ensures that the parser correctly parses various statement types.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class ParserStatementTest {

    /**
     * Test parsing an if statement without else clause.
     */
    @Test
    public void parserParsesIfStatementWithoutElse() {
        String text = "if true x";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.IfStatement);
        e.assertToken(SyntaxType.IfKeyword, "if");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.TrueKeyword, "true");
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "x");
    }

    /**
     * Test parsing an if statement with else clause.
     */
    @Test
    public void parserParsesIfStatementWithElse() {
        String text = "if true x else y";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.IfStatement);
        e.assertToken(SyntaxType.IfKeyword, "if");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.TrueKeyword, "true");
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "x");
        e.assertNode(SyntaxType.ElseClause);
        e.assertToken(SyntaxType.ElseKeyword, "else");
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "y");
    }

    /**
     * Test parsing an if statement with block body.
     */
    @Test
    public void parserParsesIfStatementWithBlockBody() {
        String text = "if true { x }";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.IfStatement);
        e.assertToken(SyntaxType.IfKeyword, "if");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.TrueKeyword, "true");
        e.assertNode(SyntaxType.BlockStatement);
        e.assertToken(SyntaxType.OpenBraceToken, "{");
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "x");
        e.assertToken(SyntaxType.CloseBraceToken, "}");
    }

    /**
     * Test parsing a while statement.
     */
    @Test
    public void parserParsesWhileStatement() {
        String text = "while true x";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.WhileStatement);
        e.assertToken(SyntaxType.WhileKeyword, "while");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.TrueKeyword, "true");
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "x");
    }

    /**
     * Test parsing a while statement with block body.
     */
    @Test
    public void parserParsesWhileStatementWithBlockBody() {
        String text = "while true { x }";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.WhileStatement);
        e.assertToken(SyntaxType.WhileKeyword, "while");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.TrueKeyword, "true");
        e.assertNode(SyntaxType.BlockStatement);
        e.assertToken(SyntaxType.OpenBraceToken, "{");
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "x");
        e.assertToken(SyntaxType.CloseBraceToken, "}");
    }

    /**
     * Test parsing a for statement.
     */
    @Test
    public void parserParsesForStatement() {
        String text = "for mut i = 0 i < 10 i = i + 1 x";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.ForStatement);
        e.assertToken(SyntaxType.ForKeyword, "for");
        // Initializer: mut i = 0
        e.assertNode(SyntaxType.VariableDeclaration);
        e.assertToken(SyntaxType.MutableKeyword, "mut");
        e.assertToken(SyntaxType.IdentifierToken, "i");
        e.assertToken(SyntaxType.EqualsToken, "=");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "0");
        // Condition: i < 10
        e.assertNode(SyntaxType.BinaryExpression);
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "i");
        e.assertToken(SyntaxType.LessToken, "<");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "10");
        // Iterator: i = i + 1
        e.assertNode(SyntaxType.AssignmentExpression);
        e.assertToken(SyntaxType.IdentifierToken, "i");
        e.assertToken(SyntaxType.EqualsToken, "=");
        e.assertNode(SyntaxType.BinaryExpression);
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "i");
        e.assertToken(SyntaxType.PlusToken, "+");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "1");
        // Body: x
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "x");
    }

    /**
     * Test parsing an empty block statement.
     */
    @Test
    public void parserParsesEmptyBlockStatement() {
        String text = "{ }";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.BlockStatement);
        e.assertToken(SyntaxType.OpenBraceToken, "{");
        e.assertToken(SyntaxType.CloseBraceToken, "}");
    }

    /**
     * Test parsing a nested block statement.
     */
    @Test
    public void parserParsesNestedBlockStatement() {
        String text = "{ { x } }";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.BlockStatement);
        e.assertToken(SyntaxType.OpenBraceToken, "{");
        e.assertNode(SyntaxType.BlockStatement);
        e.assertToken(SyntaxType.OpenBraceToken, "{");
        e.assertNode(SyntaxType.NameExpression);
        e.assertToken(SyntaxType.IdentifierToken, "x");
        e.assertToken(SyntaxType.CloseBraceToken, "}");
        e.assertToken(SyntaxType.CloseBraceToken, "}");
    }

    /**
     * Test parsing a mutable variable declaration.
     */
    @Test
    public void parserParsesMutableVariableDeclaration() {
        String text = "mut x = 10";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.VariableDeclaration);
        e.assertToken(SyntaxType.MutableKeyword, "mut");
        e.assertToken(SyntaxType.IdentifierToken, "x");
        e.assertToken(SyntaxType.EqualsToken, "=");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "10");
    }

    /**
     * Test parsing an immutable variable declaration.
     */
    @Test
    public void parserParsesImmutableVariableDeclaration() {
        String text = "imut y = true";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.VariableDeclaration);
        e.assertToken(SyntaxType.ImmutableKeyword, "imut");
        e.assertToken(SyntaxType.IdentifierToken, "y");
        e.assertToken(SyntaxType.EqualsToken, "=");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.TrueKeyword, "true");
    }

    /**
     * Test that parser reports error but continues parsing on missing close brace.
     */
    @Test
    public void parserReportsErrorOnMissingCloseBrace() {
        String text = "{ x";
        SyntaxTree tree = SyntaxTree.parse(text);

        assertTrue(tree.diagnostics().hasNext());
        // Parser should still produce a tree
        assertNotNull(tree.getRoot());
        assertNotNull(tree.getRoot().getStatement());
    }

    /**
     * Test that parser reports error on unexpected token but continues.
     */
    @Test
    public void parserReportsErrorOnUnexpectedToken() {
        String text = "mut = 10";
        SyntaxTree tree = SyntaxTree.parse(text);

        assertTrue(tree.diagnostics().hasNext());
        // Parser should still produce a tree
        assertNotNull(tree.getRoot());
    }

    /**
     * Test that parser can report multiple errors.
     */
    @Test
    public void parserReportsMultipleErrors() {
        String text = "{ ) }";
        SyntaxTree tree = SyntaxTree.parse(text);

        assertTrue(tree.diagnostics().hasNext());
        assertNotNull(tree.getRoot());
    }

    /**
     * Test parsing expression statement.
     */
    @Test
    public void parserParsesExpressionStatement() {
        String text = "1 + 2";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.BinaryExpression);
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "1");
        e.assertToken(SyntaxType.PlusToken, "+");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "2");
    }

    /**
     * Test parsing assignment expression.
     */
    @Test
    public void parserParsesAssignmentExpression() {
        String text = "x = 10";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.AssignmentExpression);
        e.assertToken(SyntaxType.IdentifierToken, "x");
        e.assertToken(SyntaxType.EqualsToken, "=");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "10");
    }

    /**
     * Test parsing parenthesized expression.
     */
    @Test
    public void parserParsesParenthesizedExpression() {
        String text = "(1 + 2)";
        StatementSyntax statement = SyntaxTree.parse(text).getRoot().getStatement();

        AssertingEnumerator e = new AssertingEnumerator(statement);
        e.assertNode(SyntaxType.ParenthesizedExpression);
        e.assertToken(SyntaxType.OpenParenthesisToken, "(");
        e.assertNode(SyntaxType.BinaryExpression);
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "1");
        e.assertToken(SyntaxType.PlusToken, "+");
        e.assertNode(SyntaxType.LiteralExpression);
        e.assertToken(SyntaxType.NumberToken, "2");
        e.assertToken(SyntaxType.CloseParenthesisToken, ")");
    }
}
