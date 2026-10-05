package codeanalysis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SiyoObjectTest {
    @Test
    void carriesTheStructName() {
        var point = new SiyoObject("Point");

        assertEquals("Point", point.getTypeName());
    }

    @Test
    void keepsFieldsAccessibleThroughTheMapApi() {
        var point = new SiyoObject("Point");
        point.put("x", 3);
        point.put("y", 4);

        assertEquals(3, point.get("x"));
        assertEquals(4, point.get("y"));
        assertEquals(2, point.size());
    }

    @Test
    void rendersFieldsInInsertionOrder() {
        var point = new SiyoObject("Point");
        point.put("x", 3);
        point.put("y", 4);

        assertEquals("Point { x: 3, y: 4 }", point.toString());
    }
}
