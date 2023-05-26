package codeanalysis.text;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The SourceTextTest class contains tests for the source text.
 *
 * @see <a href="https://github.com/urunsiyabend">GitHub Profile</a>
 * @author Siyabend Urun
 * @version 1.0
 */
class SourceTextTest {
    /**
     * Test if the source text correctly includes the last line.
     *
     * @param text The text data of the source text.
     * @param expectedLineCount The expected line count of the source text.
     */
    @ParameterizedTest
    @MethodSource("sourceTextProvider")
    void SourceText_IncludesLastLine(String text, int expectedLineCount) {
        SourceText sourceText = SourceText.from(text);
        assertEquals(expectedLineCount, sourceText.getLines().size());
    }

    /**
     * Provides the text data and the expected line count of the source text as a stream.
     *
     * @return A stream of text data and the expected line count of the source text.
     */
    private static Stream<Arguments> sourceTextProvider() {
        return Stream.of(
                Arguments.of(".", 1),
                Arguments.of(".\r\n", 2),
                Arguments.of(".\r\n\r\n", 3)
        );
    }
}