package org.sjf4j.node;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingFacade;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectSetTest {

    @Test
    public void testSetIsValueNode() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");
        set.add("b");

        assertEquals(NodeKind.ARRAY_SET, NodeKind.of(set));
    }

    @Test
    public void testSetIsArrayContainer1() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");

        assertEquals(1, Nodes.sizeInArray(set));
        Nodes.visitArray(set, (i, v) -> {
            assertEquals("a", v);
        });
        assertEquals("a", Nodes.getInArray(set, 0));
    }

    @Test
    public void testSetIsArrayContainer2() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");

        Nodes.addInArray(set, "b");
        assertEquals(2, Nodes.sizeInArray(set));
        assertEquals("[\"a\",\"b\"]", Sjf4j.toJsonString(set));

        JsonException e = assertThrows(JsonException.class, () -> Nodes.removeInArray(set, 0));
        assertTrue(e.getMessage().contains("Cannot remove"));
    }

    @Test
    public void testSetJsonStreamingGeneral() {
        Sjf4jConfig old = Sjf4jConfig.global();
        try {
            Sjf4jConfig.global(new Sjf4jConfig.Builder(old)
                    .streamingMode(StreamingFacade.StreamingMode.SHARED_IO)
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
        Object node = Sjf4j.deepNode(set);
        System.out.println("type: " + node.getClass().getName() + " node: " + Nodes.inspect(node));
        assertInstanceOf(Set.class, node);
        assertEquals("[\"a\"]", Sjf4j.toJsonString(node));

        Object node2 = Sjf4j.toRaw(set);
        assertInstanceOf(List.class, node2);
    }

}
