package codeanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the read-only behavior of {@link ParameterSymbol} constructors.
 */
public class ParameterSymbolTest {

    /**
     * Parameters created without an explicit mutability flag are read-only by default.
     */
    @Test
    void defaultConstructorCreatesReadOnlyParameter() {
        ParameterSymbol symbol = new ParameterSymbol("value", Integer.class);

        assertTrue(symbol.isReadOnly());
        assertEquals("value", symbol.getName());
        assertEquals(Integer.class, symbol.getType());
    }

    /**
     * A mutable parameter must not be exposed as read-only.
     */
    @Test
    void mutableParameterIsNotReadOnly() {
        ParameterSymbol symbol = new ParameterSymbol("value", true, Integer.class);

        assertFalse(symbol.isReadOnly());
    }

    /**
     * An explicitly immutable parameter remains read-only.
     */
    @Test
    void immutableParameterIsReadOnly() {
        ParameterSymbol symbol = new ParameterSymbol("value", false, Integer.class);

        assertTrue(symbol.isReadOnly());
    }
}
