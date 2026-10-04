package codeanalysis.syntax;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SeparatedSyntaxListTest {
    @Test
    public void emptyIteratorThrowsNoSuchElementException() {
        SeparatedSyntaxList<SyntaxToken> list = new SeparatedSyntaxList<>(List.of());
        Iterator<SyntaxToken> iterator = list.iterator();

        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void iteratorYieldsNodesInOrderWithoutSeparators() {
        SyntaxToken first = new SyntaxToken(SyntaxType.NumberToken, 0, "1", 1);
        SyntaxToken comma = new SyntaxToken(SyntaxType.CommaToken, 1, ",", null);
        SyntaxToken second = new SyntaxToken(SyntaxType.NumberToken, 2, "2", 2);
        SeparatedSyntaxList<SyntaxToken> list = new SeparatedSyntaxList<>(List.of(first, comma, second));
        Iterator<SyntaxToken> iterator = list.iterator();

        assertTrue(iterator.hasNext());
        assertSame(first, iterator.next());
        assertTrue(iterator.hasNext());
        assertSame(second, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void exhaustedIteratorRepeatedlyThrowsNoSuchElementException() {
        SyntaxToken first = new SyntaxToken(SyntaxType.NumberToken, 0, "1", 1);
        SyntaxToken comma = new SyntaxToken(SyntaxType.CommaToken, 1, ",", null);
        SyntaxToken second = new SyntaxToken(SyntaxType.NumberToken, 2, "2", 2);
        SeparatedSyntaxList<SyntaxToken> list = new SeparatedSyntaxList<>(List.of(first, comma, second));
        Iterator<SyntaxToken> iterator = list.iterator();
        iterator.next();
        iterator.next();

        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        assertFalse(iterator.hasNext());
    }
}
