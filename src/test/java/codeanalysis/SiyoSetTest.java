package codeanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SiyoSetTest {

    @Test
    void addsValuesAndIgnoresDuplicatesPreservingOrder() {
        SiyoSet set = new SiyoSet();

        set.add("alpha");
        set.add("beta");
        set.add("alpha");

        assertEquals(2, set.size());
        assertTrue(set.has("alpha"));
        assertTrue(set.has("beta"));
        assertEquals("alpha", set.values().get(0));
        assertEquals("beta", set.values().get(1));
    }

    @Test
    void removingAnElementUpdatesHasAndSize() {
        SiyoSet set = new SiyoSet();
        set.add("alpha");
        set.add("beta");
        set.remove("alpha");

        assertFalse(set.has("alpha"));
        assertEquals(1, set.size());
    }
}
