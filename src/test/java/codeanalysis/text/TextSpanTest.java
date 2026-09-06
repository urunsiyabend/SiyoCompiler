package codeanalysis.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSpanTest {

    /**
     * Verifies that equal text spans produce the same hash code.
     */
    @Test
    void TextSpan_EqualObjectsHaveSameHashCode() {
        TextSpan first = new TextSpan(3, 5);
        TextSpan second = new TextSpan(3, 5);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void TextSpan_FromBounds_ReturnsDerivedValues() {
        TextSpan span = TextSpan.fromBounds(4, 10);

        assertEquals(4, span.getStart());
        assertEquals(6, span.getLength());
        assertEquals(10, span.getEnd());
        assertEquals("Start: 4, Length: 6", span.toString());
    }
}