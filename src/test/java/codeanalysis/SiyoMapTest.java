 package codeanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

    public class SiyoMapTest {

        @Test
        void storesAndRetrievesValues() {
            SiyoMap map = new SiyoMap();

            map.set("language", "Siyo");

            assertTrue(map.has("language"));
            assertEquals("Siyo", map.get("language"));
            assertEquals(1, map.size());
            assertNull(map.get("missing"));
        }

        @Test
        void incrementsIntegerValues() {
            SiyoMap map = new SiyoMap();

            map.increment("visits");
            map.increment("visits");

            assertEquals(2, map.getInt("visits"));
        }
    }
