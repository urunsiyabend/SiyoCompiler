package codeanalysis.text;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TextLineTest {
    @Test
    void firstLine() {
        var sourceText = SourceText.from("one\r\ntwo");
        var line = sourceText.getLines().get(0);

        assertEquals(0, line.getStart());
        assertEquals(3, line.getLength());
        assertEquals(3, line.getEnd());
        assertEquals(5, line.getLengthIncludingLineBreak());
        assertEquals(new TextSpan(0, 3), line.getSpan());
        assertEquals(new TextSpan(0, 5), line.getSpanIncludingLineBreak());
        assertEquals("one", line.toString());
    }
}
