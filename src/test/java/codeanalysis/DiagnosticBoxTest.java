package codeanalysis;

import codeanalysis.text.TextSpan;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the iterator and peek contract of {@link DiagnosticBox} itself.
 * Metadata of a single {@link Diagnostic} is covered elsewhere.
 */
class DiagnosticBoxTest {

    private static Diagnostic diagnostic(int start, String message) {
        return new Diagnostic(new TextSpan(start, 1), message);
    }

    @Test
    void emptyBoxHasNoElementsAndNoNext() {
        DiagnosticBox box = new DiagnosticBox();

        assertEquals(0, box.size());
        assertFalse(box.hasNext());
    }

    @Test
    void peekOnEmptyBoxThrowsNoSuchElementException() {
        DiagnosticBox box = new DiagnosticBox();

        assertThrows(NoSuchElementException.class, box::peek);
    }

    @Test
    void nextOnEmptyBoxThrowsNoSuchElementException() {
        DiagnosticBox box = new DiagnosticBox();

        assertThrows(NoSuchElementException.class, box::next);
    }

    @Test
    void peekReturnsNextDiagnosticWithoutConsumingIt() {
        DiagnosticBox box = new DiagnosticBox();
        Diagnostic first = diagnostic(0, "first");
        box.add(first).add(diagnostic(1, "second"));

        assertSame(first, box.peek());
        assertSame(first, box.peek());
        assertTrue(box.hasNext());
        assertSame(first, box.next());
    }

    @Test
    void nextReturnsDiagnosticsInInsertionOrderAndAdvances() {
        DiagnosticBox box = new DiagnosticBox();
        Diagnostic first = diagnostic(0, "first");
        Diagnostic second = diagnostic(1, "second");
        box.add(first).add(second);

        assertSame(first, box.next());
        assertSame(second, box.peek());
        assertSame(second, box.next());
        assertFalse(box.hasNext());
    }

    @Test
    void getAndSizeDoNotAdvanceIteration() {
        DiagnosticBox box = new DiagnosticBox();
        Diagnostic first = diagnostic(0, "first");
        Diagnostic second = diagnostic(1, "second");
        box.add(first).add(second);

        assertEquals(2, box.size());
        assertSame(first, box.get(0));
        assertSame(second, box.get(1));
        assertSame(first, box.peek());
        assertSame(first, box.next());
    }

    @Test
    void exhaustedBoxKeepsThrowingOnPeekAndNext() {
        DiagnosticBox box = new DiagnosticBox();
        box.add(diagnostic(0, "first")).add(diagnostic(1, "second"));
        box.next();
        box.next();

        assertFalse(box.hasNext());
        assertThrows(NoSuchElementException.class, box::peek);
        assertThrows(NoSuchElementException.class, box::peek);
        assertThrows(NoSuchElementException.class, box::next);
        assertThrows(NoSuchElementException.class, box::next);
        assertEquals(2, box.size());
    }
}
