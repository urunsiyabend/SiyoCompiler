package codeanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SiyoUnionTest {

    @Test
    void exposesVariantAndPayload() {
        SiyoUnion value = SiyoUnion.of("Result", "Ok", 42);

        assertEquals("Result", value.getTypeName());
        assertEquals("Ok", value.getVariantName());
        assertEquals(1, value.size());
        assertEquals(42, value.get(0));
        assertTrue(value.is("Ok"));
        assertFalse(value.is("Err"));
        assertEquals("Ok(42)", value.toString());
    }

    @Test
    void identicalValuesAreEqualAndHaveEqualHashCodes() {
        SiyoUnion first = SiyoUnion.of("Result", "Ok", 42);
        SiyoUnion second = SiyoUnion.of("Result", "Ok", 42);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
