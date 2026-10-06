package codeanalysis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SiyoThrowTest {
    @Test
    void textPayloadIsAlsoTheMessage() {
        var thrown = new SiyoThrow("boom");

        assertEquals("boom", thrown.getPayload());
        assertEquals("boom", thrown.getMessage());
    }

    @Test
    void nonTextPayloadIsPreservedAndRenderedAsTheMessage() {
        var thrown = new SiyoThrow(42);

        assertEquals(42, thrown.getPayload());
        assertEquals("42", thrown.getMessage());
    }

    @Test
    void nullPayloadIsPreservedAndRenderedAsNullText() {
        var thrown = new SiyoThrow(null);

        assertNull(thrown.getPayload());
        assertEquals("null", thrown.getMessage());
    }
}
