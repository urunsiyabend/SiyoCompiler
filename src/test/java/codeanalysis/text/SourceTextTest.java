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
     * Test that positions at line starts, inside lines, and on line breaks map to the expected line.
     */
    @org.junit.jupiter.api.Test
    void SourceText_MapsPositionsToLineIndexes() {
        SourceText sourceText = SourceText.from("ab\ncd\nef");

        assertEquals(0, sourceText.getLineIndex(0));
        assertEquals(0, sourceText.getLineIndex(1));
        assertEquals(0, sourceText.getLineIndex(2));
        assertEquals(1, sourceText.getLineIndex(3));
        assertEquals(1, sourceText.getLineIndex(4));
        assertEquals(1, sourceText.getLineIndex(5));
        assertEquals(2, sourceText.getLineIndex(6));
        assertEquals(2, sourceText.getLineIndex(7));
    }

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
                Arguments.of("", 1),
                Arguments.of(".\n", 2),
                Arguments.of(".\n\n", 3),
                Arguments.of(".\r", 2),
                Arguments.of(".\r\n", 2),
                Arguments.of(".\r\n\r\n", 3)
        );
    }
}
