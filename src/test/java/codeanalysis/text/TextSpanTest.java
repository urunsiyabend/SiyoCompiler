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
}