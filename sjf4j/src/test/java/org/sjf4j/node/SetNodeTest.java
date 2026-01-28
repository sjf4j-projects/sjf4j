package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonException;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SetNodeTest {

    @Test
    public void testSetIsValueNode() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");
        set.add("b");

        assertEquals(NodeType.VALUE_SET, NodeType.of(set));
    }

    @Test
    public void testSetIsNotArrayContainer() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");

        assertThrows(JsonException.class, () -> Nodes.sizeInArray(set));
        assertThrows(JsonException.class, () -> Nodes.visitArray(set, (i, v) -> {}));
        assertThrows(JsonException.class, () -> Nodes.getInArray(set, 0));
    }
    
}
