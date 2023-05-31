package codeanalysis;

import codeanalysis.syntax.SyntaxTree;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    @MethodSource("testData")
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
     * Provides all the test data as a stream.
     *
     * @return A stream of test data.
     */
    private static Object[][] testData() {
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
                {"{ {mut x = 1} mut x = 10 x}", 10}
        };
    }
}