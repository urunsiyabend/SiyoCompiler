package codeanalysis.binding;

import codeanalysis.Compilation;
import codeanalysis.EvaluationResult;
import codeanalysis.VariableSymbol;
import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The BinderTest class contains tests for the binder.
 * It ensures that the binder correctly binds expressions and statements.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class BinderTest {

    /**
     * Test that integer literals are bound correctly.
     */
    @Test
    public void binderBindsIntegerLiteral() {
        assertBindsWithType("42", Integer.class);
    }

    /**
     * Test that boolean literals are bound correctly.
     */
    @Test
    public void binderBindsBooleanLiteralTrue() {
        assertBindsWithType("true", Boolean.class);
    }

    /**
     * Test that boolean false literal is bound correctly.
     */
    @Test
    public void binderBindsBooleanLiteralFalse() {
        assertBindsWithType("false", Boolean.class);
    }

    /**
     * Test that arithmetic operators produce integer result.
     */
    @ParameterizedTest
    @MethodSource("arithmeticOperatorsData")
    public void binderBindsArithmeticOperatorsAsInteger(String text) {
        assertBindsWithType(text, Integer.class);
    }

    private static Object[][] arithmeticOperatorsData() {
        return new Object[][] {
                {"1 + 2"},
                {"1 - 2"},
                {"1 * 2"},
                {"1 / 2"},
                {"1 % 2"},
        };
    }

    /**
     * Test that comparison operators produce boolean result.
     */
    @ParameterizedTest
    @MethodSource("comparisonOperatorsData")
    public void binderBindsComparisonOperatorsAsBoolean(String text) {
        assertBindsWithType(text, Boolean.class);
    }

    private static Object[][] comparisonOperatorsData() {
        return new Object[][] {
                {"1 < 2"},
                {"1 <= 2"},
                {"1 > 2"},
                {"1 >= 2"},
                {"1 == 2"},
                {"1 != 2"},
                {"true == false"},
                {"true != false"},
        };
    }

    /**
     * Test that logical operators produce boolean result.
     */
    @ParameterizedTest
    @MethodSource("logicalOperatorsData")
    public void binderBindsLogicalOperatorsAsBoolean(String text) {
        assertBindsWithType(text, Boolean.class);
    }

    private static Object[][] logicalOperatorsData() {
        return new Object[][] {
                {"true && false"},
                {"true || false"},
        };
    }

    /**
     * Test that bitwise operators on integers produce integer result.
     */
    @ParameterizedTest
    @MethodSource("bitwiseIntegerOperatorsData")
    public void binderBindsBitwiseIntegerOperatorsAsInteger(String text) {
        assertBindsWithType(text, Integer.class);
    }

    private static Object[][] bitwiseIntegerOperatorsData() {
        return new Object[][] {
                {"1 & 2"},
                {"1 | 2"},
                {"1 ^ 2"},
                {"1 << 2"},
                {"1 >> 2"},
                {"~1"},
        };
    }

    /**
     * Test that bitwise operators on booleans produce boolean result.
     */
    @ParameterizedTest
    @MethodSource("bitwiseBooleanOperatorsData")
    public void binderBindsBitwiseBooleanOperatorsAsBoolean(String text) {
        assertBindsWithType(text, Boolean.class);
    }

    private static Object[][] bitwiseBooleanOperatorsData() {
        return new Object[][] {
                {"true & false"},
                {"true | false"},
                {"true ^ false"},
        };
    }

    /**
     * Test that unary operators on integers produce integer result.
     */
    @ParameterizedTest
    @MethodSource("unaryIntegerOperatorsData")
    public void binderBindsUnaryIntegerOperatorsAsInteger(String text) {
        assertBindsWithType(text, Integer.class);
    }

    private static Object[][] unaryIntegerOperatorsData() {
        return new Object[][] {
                {"+1"},
                {"-1"},
                {"~1"},
        };
    }

    /**
     * Test that unary not operator on boolean produces boolean result.
     */
    @Test
    public void binderBindsUnaryNotOperatorAsBoolean() {
        assertBindsWithType("!true", Boolean.class);
    }

    /**
     * Test variable declaration and access.
     */
    @Test
    public void binderBindsVariableDeclarationAndAccess() {
        String text = "{ mut x = 10 x }";
        assertBindsWithType(text, Integer.class);
        assertEvaluatesTo(text, 10);
    }

    /**
     * Test variable shadowing in inner scope.
     */
    @Test
    public void binderAllowsVariableShadowingInInnerScope() {
        String text = "{ mut x = 10 { mut x = 20 x } }";
        assertEvaluatesTo(text, 20);
    }

    /**
     * Test that outer variable is accessible after inner scope.
     */
    @Test
    public void binderAccessesOuterVariableAfterInnerScope() {
        String text = "{ mut x = 10 { mut x = 20 } x }";
        assertEvaluatesTo(text, 10);
    }

    /**
     * Test that variable is not accessible outside its scope.
     */
    @Test
    public void binderReportsUndefinedVariableOutsideScope() {
        String text = "{ { mut x = 10 } x }";
        assertHasDiagnostics(text);
    }

    /**
     * Test that redeclaring variable in same scope reports error.
     */
    @Test
    public void binderReportsVariableRedeclaration() {
        String text = "{ mut x = 10 mut x = 20 }";
        assertHasDiagnostics(text);
    }

    /**
     * Test that assigning to undefined variable reports error.
     */
    @Test
    public void binderReportsAssignmentToUndefinedVariable() {
        String text = "{ x = 10 }";
        assertHasDiagnostics(text);
    }

    /**
     * Test that assigning to immutable variable reports error.
     */
    @Test
    public void binderReportsAssignmentToImmutableVariable() {
        String text = "{ imut x = 10 x = 20 }";
        assertHasDiagnostics(text);
    }

    /**
     * Test that assigning wrong type reports error.
     */
    @Test
    public void binderReportsTypeMismatchOnAssignment() {
        String text = "{ mut x = 10 x = true }";
        assertHasDiagnostics(text);
    }

    /**
     * Test that if condition must be boolean.
     */
    @Test
    public void binderReportsNonBooleanIfCondition() {
        String text = "if 1 { true }";
        assertHasDiagnostics(text);
    }

    /**
     * Test that while condition must be boolean.
     */
    @Test
    public void binderReportsNonBooleanWhileCondition() {
        String text = "while 1 { true }";
        assertHasDiagnostics(text);
    }

    /**
     * Test that for condition must be boolean.
     */
    @Test
    public void binderReportsNonBooleanForCondition() {
        String text = "for mut i = 0 i i = i + 1 { true }";
        assertHasDiagnostics(text);
    }

    /**
     * Test that valid if statement binds without errors.
     */
    @Test
    public void binderBindsValidIfStatement() {
        String text = "{ mut x = 0 if true { x = 1 } x }";
        assertNoErrors(text);
        assertEvaluatesTo(text, 1);
    }

    /**
     * Test that valid while statement binds without errors.
     */
    @Test
    public void binderBindsValidWhileStatement() {
        String text = "{ mut x = 0 while x < 5 { x = x + 1 } x }";
        assertNoErrors(text);
        assertEvaluatesTo(text, 5);
    }

    /**
     * Test that valid for statement binds without errors.
     */
    @Test
    public void binderBindsValidForStatement() {
        String text = "{ mut sum = 0 for mut i = 0 i < 5 i = i + 1 { sum = sum + i } sum }";
        assertNoErrors(text);
        assertEvaluatesTo(text, 10); // 0 + 1 + 2 + 3 + 4 = 10
    }

    /**
     * Test undefined unary operator reports error.
     */
    @Test
    public void binderReportsUndefinedUnaryOperator() {
        String text = "+true";
        assertHasDiagnostics(text);
    }

    /**
     * Test undefined binary operator reports error.
     */
    @Test
    public void binderReportsUndefinedBinaryOperator() {
        String text = "true + 1";
        assertHasDiagnostics(text);
    }

    /**
     * Helper to assert that text binds to expected type.
     */
    private void assertBindsWithType(String text, Class<?> expectedType) {
        SyntaxTree tree = SyntaxTree.parse(text);
        Compilation compilation = new Compilation(tree);
        try {
            EvaluationResult result = compilation.evaluate(new HashMap<>());
            assertFalse(result.diagnostics().hasNext(), "Expected no diagnostics");
            assertNotNull(result.getValue());
            assertEquals(expectedType, result.getValue().getClass());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Helper to assert that text evaluates to expected value.
     */
    private void assertEvaluatesTo(String text, Object expectedValue) {
        SyntaxTree tree = SyntaxTree.parse(text);
        Compilation compilation = new Compilation(tree);
        try {
            EvaluationResult result = compilation.evaluate(new HashMap<>());
            assertFalse(result.diagnostics().hasNext(), "Expected no diagnostics");
            assertEquals(expectedValue, result.getValue());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Helper to assert that text has diagnostics (errors).
     */
    private void assertHasDiagnostics(String text) {
        SyntaxTree tree = SyntaxTree.parse(text);
        Compilation compilation = new Compilation(tree);
        try {
            EvaluationResult result = compilation.evaluate(new HashMap<>());
            assertTrue(result.diagnostics().hasNext(), "Expected diagnostics but found none");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Helper to assert that text has no errors.
     */
    private void assertNoErrors(String text) {
        SyntaxTree tree = SyntaxTree.parse(text);
        Compilation compilation = new Compilation(tree);
        try {
            EvaluationResult result = compilation.evaluate(new HashMap<>());
            assertFalse(result.diagnostics().hasNext(), "Expected no diagnostics");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }
}
