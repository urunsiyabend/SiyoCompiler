package codeanalysis.binding;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A type table changes only through recorded puts and removes, each a new generation. */
class VersionedMapTest {
    @Test
    void everyWayToChangeATableIsRecordedOrRefused() {
        VersionedMap.Version version = new VersionedMap.Version();
        VersionedMap<String, String> table = new VersionedMap<>(version);
        table.put("P", "lib");
        long generation = version.value();

        Map.Entry<String, String> entry = table.entrySet().iterator().next();
        assertThrows(UnsupportedOperationException.class, () -> entry.setValue("other"));
        assertThrows(UnsupportedOperationException.class, () -> table.entrySet().iterator().remove());
        assertThrows(UnsupportedOperationException.class, () -> table.keySet().remove("P"));
        assertThrows(UnsupportedOperationException.class, () -> table.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> table.compute("P", (k, v) -> "other"));
        assertThrows(UnsupportedOperationException.class, () -> table.putAll(Map.of("P", "other")));
        assertEquals("lib", table.get("P"));
        assertEquals(generation, version.value());

        // A recorded change is a new generation, and the old one still reads as it was.
        table.put("P", "other");
        assertEquals(generation + 1, version.value());
        assertEquals("lib", table.asOf(generation).get("P"));
        assertEquals("other", table.asOf(version.value()).get("P"));
        assertNull(table.asOf(0).get("P"));
        // Putting what is already there changes nothing.
        table.put("P", "other");
        assertEquals(generation + 1, version.value());
    }
}
