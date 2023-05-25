package codeanalysis.syntax;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The SyntaxRulesTest class contains tests for the syntax rules.
 * It ensures that the syntax rules are correctly implemented.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
public class SyntaxRulesTest {
    /**
     * Test if the syntax rules correctly parse the source code into tokens.
     *
     * @param type The syntax type of the token.
     */
    @ParameterizedTest
    @MethodSource("getSyntaxTypeData")
    void syntaxFactGetTextRoundTrips(SyntaxType type) {
        String text = SyntaxRules.getTextData(type);
        if (text == null)
            return;

        ArrayList<SyntaxToken> tokens = (ArrayList<SyntaxToken>) SyntaxTree.parseTokens(text);
        var token = tokens.get(0);
        assertEquals(type, token.getType());
        assertEquals(text, token.getData());
    }

    /**
     * Provides all the syntax types as a stream.
     *
     * @return A stream of syntax types.
     */
    static Stream<SyntaxType> getSyntaxTypeData() {
        SyntaxType[] types = SyntaxType.values();
        return Arrays.stream(types);
    }
}
