package org.sjf4j;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class JsonArraySelfAddTest {

    @Test
    void selfAddAppendsOnlyOriginalElements() {
        JsonObject child = JsonObject.of("id", 1);
        JsonArray array = JsonArray.of(child, null, 3);

        array.addAll(array);

        assertEquals(6, array.size());
        assertSame(child, array.getNode(0));
        assertSame(child, array.getNode(3));
        assertNull(array.getNode(1));
        assertNull(array.getNode(4));
        assertEquals(3, array.getNode(2));
        assertEquals(3, array.getNode(5));
    }

    @Test
    void emptyAndTypedArraysCanAppendThemselves() {
        JsonArray empty = new JsonArray();
        empty.addAll(empty);
        assertTrue(empty.isEmpty());

        JsonArray integers = new JsonArray() {
            @Override
            public Class<?> elementClass() {
                return Integer.class;
            }
        };
        integers.add(1);
        integers.add(2);
        integers.addAll(integers);
        assertEquals(Arrays.asList(1, 2, 1, 2), integers.toList());
    }
}
