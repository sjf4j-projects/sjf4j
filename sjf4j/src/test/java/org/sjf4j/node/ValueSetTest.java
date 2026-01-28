package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonException;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValueSetTest {

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

    @Test
    public void testSetJsonStreamingGeneral() {
        Sjf4jConfig old = Sjf4jConfig.global();
        try {
            Sjf4jConfig.global(new Sjf4jConfig.Builder(old)
                    .readMode(Sjf4jConfig.ReadMode.STREAMING_GENERAL)
                    .writeMode(Sjf4jConfig.WriteMode.STREAMING_GENERAL)
                    .build());

            Set<String> set = new LinkedHashSet<>();
            set.add("a");
            set.add("b");

            String json = Sjf4j.toJsonString(set);
            assertEquals("[\"a\",\"b\"]", json);

            Set<String> parsed = Sjf4j.fromJson(json, new TypeReference<Set<String>>() {});
            assertEquals(set, parsed);
        } finally {
            Sjf4jConfig.global(old);
        }
    }

    @Test
    public void testSetFromNode() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");
        Object node = Sjf4j.fromNode(set);
        System.out.println("type: " + node.getClass().getName() + " node: " + Nodes.inspect(node));
        assertInstanceOf(Set.class, node);
        assertEquals("[\"a\"]", Sjf4j.toJsonString(node));

        Object node2 = Sjf4j.toRaw(set);
        assertInstanceOf(List.class, node2);
    }

}
