package codeanalysis.syntax;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SyntaxRulesTest {
    @ParameterizedTest
    @MethodSource("getSyntaxTypeData")
    void syntaxFactGetTextRoundTrips(SyntaxType kind) {
        String text = SyntaxRules.getTextData(kind);
        if (text == null)
            return;

        ArrayList<SyntaxToken> tokens = (ArrayList<SyntaxToken>) SyntaxTree.parseTokens(text);
        var token = tokens.get(0);
        assertEquals(kind, token.getType());
        assertEquals(text, token.getData());
    }

    static Stream<SyntaxType> getSyntaxTypeData() {
        SyntaxType[] types = SyntaxType.values();
        return Arrays.stream(types);
    }
}
