package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.NodeException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodeMapperDirectTest {

    @Test
    void directAdaptationRejectsInvalidValuesAndKeepsEmptyCopiesMutable() {
        RuntimeContext ctx = RuntimeContext.EMPTY;

        // Lenient string adaptation accepts scalar values only.
        assertEquals("123", NodeMapper.asString(123, ctx));
        assertEquals("true", NodeMapper.asString(true, ctx));

        assertThrows(NodeException.class,
                () -> NodeMapper.asString(new LinkedHashMap<>(), ctx));
        assertThrows(NodeException.class,
                () -> NodeMapper.asString(new ArrayList<>(), ctx));

        // A target class is required.
        assertThrows(NullPointerException.class,
                () -> NodeMapper.to("x", null, ctx));
        assertThrows(NullPointerException.class,
                () -> NodeMapper.as("x", null, ctx));

        // Numeric booleans must be exactly 0 or 1.
        assertTrue(NodeMapper.asBoolean(1, ctx));
        assertFalse(NodeMapper.asBoolean(0, ctx));

        assertThrows(NodeException.class,
                () -> NodeMapper.asBoolean(1.5, ctx));
        assertThrows(NodeException.class,
                () -> NodeMapper.asBoolean(4294967297L, ctx));

        // Array snapshots should remain mutable even when empty.
        JsonArray empty = new JsonArray();

        List<Object> list = empty.toList();
        list.add("x");
        assertTrue(empty.isEmpty());

        Set<Object> set = empty.toSet();
        set.add("x");
        assertTrue(empty.isEmpty());
    }

}
