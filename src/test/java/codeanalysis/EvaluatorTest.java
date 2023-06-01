package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import codeanalysis.text.TextSpan;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The EvaluatorTest class contains tests for the evaluator.
 * It ensures that the evaluator correctly evaluates the syntax tree.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
class EvaluatorTest {
    /**
     * Test if the evaluator correctly evaluates the syntax tree.
     *
     * @param text The text to be evaluated.
     * @param expectedValue The expected value of the evaluated text.
     */
    @ParameterizedTest
    @MethodSource("evaluatorComputesCorrectValuesTestData")
    void evaluatorComputesCorrectValues(String text, Object expectedValue) {
        SyntaxTree tree = SyntaxTree.parse(text);
        Compilation compilation = new Compilation(tree);
        HashMap<VariableSymbol, Object> variables = new HashMap<>();
        try {
            EvaluationResult result = compilation.evaluate(variables);
            assertFalse(result._diagnostics.hasNext());
            assertEquals(expectedValue, result.getValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test if the evaluator correctly reports diagnostics for redeclaration of variables.
     * The test data is provided by the evaluatorReportsRedeclarationForVariablesTestData method.
     *
     * @param text The text to be evaluated.
     * @param diagnosticText The expected diagnostic text.
     * @see #evaluatorReportsRedeclarationForVariablesTestData()
     */
    @ParameterizedTest
    @MethodSource("evaluatorReportsRedeclarationForVariablesTestData")
    void evaluatorReportsRedeclarationForVariables(String text, String diagnosticText) {
        assertHasDiagnostics(text, diagnosticText);
    }

    /**
     * Test if the evaluator correctly reports diagnostics for undefined variables.
     * The test data is provided by the evaluatorReportsUndefinedTestData method.
     *
     * @param text The text to be evaluated.
     * @param diagnosticText The expected diagnostic text.
     * @see #evaluatorReportsUndefinedTestData()
     */
    @ParameterizedTest
    @MethodSource("evaluatorReportsUndefinedTestData")
    void evaluatorReportsUndefined(String text, String diagnosticText) {
        assertHasDiagnostics(text, diagnosticText);
    }

    /**
     * Test if the evaluator correctly reports diagnostics for reassignment of read only variables.
     * The test data is provided by the evaluatorReportsCannotAssignTestData method.
     *
     * @param text The text to be evaluated.
     * @param diagnosticText The expected diagnostic text.
     * @see #evaluatorReportsCannotAssignTestData()
     */
    @ParameterizedTest
    @MethodSource("evaluatorReportsCannotAssignTestData")
    void evaluatorReportsCannotAssign(String text, String diagnosticText) {
        assertHasDiagnostics(text, diagnosticText);
    }

    /**
     * Test if the evaluator correctly reports diagnostics for conversion between types.
     * The test data is provided by the evaluatorReportsCannotConvertTestData method.
     *
     * @param text The text to be evaluated.
     * @param diagnosticText The expected diagnostic text.
     * @see #evaluatorReportsCannotConvertTestData()
     */
    @ParameterizedTest
    @MethodSource("evaluatorReportsCannotConvertTestData")
    void evaluatorReportsCannotConvert(String text, String diagnosticText) {
        assertHasDiagnostics(text, diagnosticText);
    }

    /**
     * Test if the evaluator correctly reports diagnostics for undefined unary operators.
     * The test data is provided by the evaluatorReportsUndefinedUnaryOperatorTestData method.
     *
     * @param text The text to be evaluated.
     * @param diagnosticText The expected diagnostic text.
     * @see #evaluatorReportsUndefinedUnaryOperatorTestData()
     */
    @ParameterizedTest
    @MethodSource("evaluatorReportsUndefinedUnaryOperatorTestData")
    void evaluatorReportsUndefinedUnaryOperator(String text, String diagnosticText) {
        assertHasDiagnostics(text, diagnosticText);
    }

    /**
     * Test if the evaluator correctly reports diagnostics for undefined binary operators.
     * The test data is provided by the evaluatorReportsUndefinedBinaryOperatorTestData method.
     *
     * @param text The text to be evaluated.
     * @param diagnosticText The expected diagnostic text.
     * @see #evaluatorReportsUndefinedBinaryOperatorTestData()
     */
    @ParameterizedTest
    @MethodSource("evaluatorReportsUndefinedBinaryOperatorTestData")
    void evaluatorReportsUndefinedBinaryOperator(String text, String diagnosticText) {
        assertHasDiagnostics(text, diagnosticText);
    }

    /**
     * Helper function for the report tests.
     * It asserts that the given text has the given diagnostic text.
     *
     * @param text The text to be evaluated.
     * @param diagnosticText The expected diagnostic text.
     */
    private void assertHasDiagnostics(String text, String diagnosticText) {
        AnnotatedText annotatedText = AnnotatedText.parse(text);
        SyntaxTree tree = SyntaxTree.parse(annotatedText.getText());
        Compilation compilation = new Compilation(tree);
        try {
            EvaluationResult result = compilation.evaluate(new HashMap<>());
            ArrayList<String> expectedDiagnostics = AnnotatedText.unIndentLines(diagnosticText);
            if (annotatedText.getSpans().size() != expectedDiagnostics.size()) {
                throw new IllegalArgumentException("ERROR: The number of diagnostics does not match the number of spans in the text.");
            }

            assertEquals(expectedDiagnostics.size(), result._diagnostics.size());

            for (int i = 0; i < expectedDiagnostics.size(); i++) {
                String expectedMessage = expectedDiagnostics.get(i);
                String actualMessage = result._diagnostics.get(i).getMessage();
                assertEquals(expectedMessage, actualMessage);

                TextSpan expectedSpan = annotatedText.getSpans().get(i);
                TextSpan actualSpan = result._diagnostics.get(i).getSpan();
                assertEquals(expectedSpan, actualSpan);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Provides the test data for the evaluatorComputesCorrectValues test.
     *
     * @return The test data for the evaluatorComputesCorrectValues test.
     */
    private static Object[][] evaluatorComputesCorrectValuesTestData() {
        return new Object[][] {
                {"1", 1},
                {"+1", 1},
                {"-1", -1},
                {"14 + 12", 26},
                {"12 - 3", 9},
                {"4 * 2", 8},
                {"9 / 3", 3},
                {"(10)", 10},
                {"12 == 3", false},
                {"3 == 3", true},
                {"12 != 3", true},
                {"3 != 3", false},
                {"0 < 0", false},
                {"0 <= 0", true},
                {"0 > 0", false},
                {"0 >= 0", true},
                {"1 < 0", false},
                {"1 <= 0", false},
                {"1 > 0", true},
                {"1 >= 0", true},
                {"true == true", true},
                {"false == false", true},
                {"true == false", false},
                {"false != false", false},
                {"true != false", true},
                {"true || false", true},
                {"false || false", false},
                {"false || true", true},
                {"false || false", false},
                {"true && true", true},
                {"true && false", false},
                {"false && true", false},
                {"false && false", false},
                {"true", true},
                {"false", false},
                {"!true", false},
                {"!false", true},
                {"{ mut a = 0 (a = 10) * a }", 100},
                {"{ imut b = 0 {imut b = true} b }", 0},
                {"{ imut b = 0 {imut b = true b} }", true},
                {"{ {mut x = 1} mut x = 10 x}", 10},
                {"{ mut x = 1 if x == 1 x = 2 x}", 2},
                {"{ mut x = 1 if x == 0 x = 2 x}", 1},
                {"{ mut x = 1 if x == 1 x = 2 else x = 3 x}", 2},
                {"{ mut x = 1 if x == 0 x = 2 else x = 3 x}", 3},
        };
    }

    /**
     * Provides the test data for the evaluatorReportsRedeclarationForVariables test.
     *
     * @return The test data for the evaluatorReportsRedeclarationForVariables test.
     */
    private static Object[][] evaluatorReportsRedeclarationForVariablesTestData() {
        return new Object[][] {
                {"""
                {
                    mut x = 10
                    mut y = 100
                    {
                        mut x = 10
                    }
                
                    mut [x] = 5
                }
                """, "Variable 'x' is already declared"},
                {"""
                {
                    mut y = 10
                    mut [y] = false
                }
                """, "Variable 'y' is already declared"},
                {"""
                {
                    {
                        mut z = 20
                        mut [z] = false
                    }
                }
                """, "Variable 'z' is already declared"},
        };
    }

    /**
     * Provides the test data for the evaluatorReportsUndefinedTestData test.
     *
     * @return The test data for the evaluatorReportsUndefinedTestData test.
     */
    public static Object[][] evaluatorReportsUndefinedTestData() {
        return new Object[][] {
                {"""
                {
                    [x] = 10
                }
                """, "Name 'x' does not exist"},
                {"""
                {
                    {
                        [z] = false
                    }
                }
                """, "Name 'z' does not exist"},
                {"""
                {
                    {
                        imut z = 10
                    }
                    [z] = false
                }
                """, "Name 'z' does not exist"},
                {"""
                {
                    {
                        imut z = 10
                    }
                    {
                        [z] = false
                    }
                }
                """, "Name 'z' does not exist"},
                {"""
                {
                    {
                        imut ax = 100
                    }
                    [ax] = 10
                }
                """, "Name 'ax' does not exist"},
        };
    }

    /**
     * Provides the test data for the evaluatorReportsUndefinedUnaryOperatorTestData test.
     *
     * @return The test data for the evaluatorReportsUndefinedUnaryOperatorTestData test.
     */
    public static Object[][] evaluatorReportsCannotAssignTestData() {
        return new Object[][] {
                {"""
                {
                    imut x = 10
                    x [=] false
                }
                """, "Name 'x' is read-only and cannot be assigned"},
                {"""
                {
                    {
                        mut x = 100
                    }
                    imut x = 10
                    x [=] false
                }
                """, "Name 'x' is read-only and cannot be assigned"},
                {"""
                {
                    mut z = 10
                    {
                        imut z = 200
                        z [=] 10
                    }
                }
                """, "Name 'z' is read-only and cannot be assigned"},
                {"""
                {
                    {
                        imut z = 100
                    }
                    imut z = 10
                    z [=] false
                }
                """, "Name 'z' is read-only and cannot be assigned"},
        };
    }

    /**
     * Provides the test data for the evaluatorReportsCannotConvertTestData test.
     *
     * @return The test data for the evaluatorReportsCannotConvertTestData test.
     */
    public static Object[][] evaluatorReportsCannotConvertTestData() {
        return new Object[][] {
                {"""
                {
                    mut x = 10
                    x = [false]
                }
                """, "Cannot convert type <class java.lang.Boolean> to <class java.lang.Integer>"},
                {"""
                {
                    mut z = false
                    z = [10]
                }
                """, "Cannot convert type <class java.lang.Integer> to <class java.lang.Boolean>"},
        };
    }

    /**
     * Provides the test data for the evaluatorReportsUndefinedUnaryOperatorTestData test.
     *
     * @return The test data for the evaluatorReportsUndefinedUnaryOperatorTestData test.
     */
    public static Object[][] evaluatorReportsUndefinedUnaryOperatorTestData() {
        return new Object[][] {
                {"""
                {
                    [+]true
                }
                """, "Unary operator '+' is not defined for type <class java.lang.Boolean>"},
                {"""
                {
                    [-]true
                }
                """, "Unary operator '-' is not defined for type <class java.lang.Boolean>"},
                {"""
                {
                    [!]10
                }
                """, "Unary operator '!' is not defined for type <class java.lang.Integer>"},
        };
    }

    /**
     * Provides the test data for the evaluatorReportsUndefinedBinaryOperatorTestData test.
     *
     * @return The test data for the evaluatorReportsUndefinedBinaryOperatorTestData test.
     */
    public static Object[][] evaluatorReportsUndefinedBinaryOperatorTestData() {
        return new Object[][] {
                {"""
                {
                    true [+] 3
                }
                """, "Binary operator '+' is not defined for types <class java.lang.Boolean> and <class java.lang.Integer>"},
                {"""
                {
                    true [-] 3
                }
                """, "Binary operator '-' is not defined for types <class java.lang.Boolean> and <class java.lang.Integer>"},
                {"""
                {
                    3 [*] true
                }
                """, "Binary operator '*' is not defined for types <class java.lang.Integer> and <class java.lang.Boolean>"},
                {"""
                {
                    true [/] true
                }
                """, "Binary operator '/' is not defined for types <class java.lang.Boolean> and <class java.lang.Boolean>"},
                {"""
                {
                    3 [||] true
                }
                """, "Binary operator '||' is not defined for types <class java.lang.Integer> and <class java.lang.Boolean>"},
                {"""
                {
                    true [&&] 10
                }
                """, "Binary operator '&&' is not defined for types <class java.lang.Boolean> and <class java.lang.Integer>"},
                {"""
                {
                    true [==] 10
                }
                """, "Binary operator '==' is not defined for types <class java.lang.Boolean> and <class java.lang.Integer>"},
        };
    }
}