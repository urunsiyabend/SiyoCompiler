package codeanalysis;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class SiyoArrayTest {

    @Test
    void supportsBasicCollectionOperations() {
        SiyoArray array = new SiyoArray(Arrays.asList(1, 2), Integer.class);

        assertEquals(2, array.size());
        assertEquals(2, array.length());
        assertEquals(Integer.class, array.getElementType());
        assertEquals(1, array.get(0));

        array.set(0, 3);
        array.add(4);
        array.remove(1);

        assertEquals("[3, 4]", array.toString());
    }
}