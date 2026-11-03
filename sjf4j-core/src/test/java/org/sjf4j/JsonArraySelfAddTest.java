package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class JsonArraySelfAddTest {

    @Test
    void selfAddIsRejectedWithoutMutation() {
        JsonObject child = JsonObject.of("id", 1);
        JsonArray array = JsonArray.of(child, null, 3);

        NodeException error = assertThrows(NodeException.class, () -> array.addAll(array));

        assertTrue(error.getMessage().contains("itself"));
        assertEquals(3, array.size());
        assertSame(child, array.getNode(0));
        assertNull(array.getNode(1));
        assertEquals(3, array.getNode(2));
    }

    @Test
    void emptyAndTypedArraysAlsoRejectSelfAdd() {
        JsonArray empty = new JsonArray();
        assertThrows(NodeException.class, () -> empty.addAll(empty));
        assertTrue(empty.isEmpty());

        JsonArray integers = new JsonArray() {
            @Override
            public Class<?> elementClass() {
                return Integer.class;
            }
        };
        integers.add(1);
        integers.add(2);
        assertThrows(NodeException.class, () -> integers.addAll(integers));
        assertEquals(Arrays.asList(1, 2), integers.toList());
    }

    @Test
    void distinctArraySourcesStillAppendNormally() {
        JsonArray target = JsonArray.of(1);
        JsonArray source = JsonArray.of(2, null, 3);
        target.addAll(source);

        assertEquals(Arrays.asList(1, 2, null, 3), target.toList());
        assertEquals(Arrays.asList(2, null, 3), source.toList());
    }
}
