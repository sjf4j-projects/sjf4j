package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectSetTest {

    @Test
    public void setIsRecognizedAsArray() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");
        set.add("b");

        assertEquals(NodeKind.ARRAY_SET, NodeKind.of(set));
    }

    @Test
    public void setSupportsArrayIteration() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");

        assertEquals(1, Nodes.sizeInArray(set));
        Nodes.forEachArray(set, (i, v) -> {
            assertEquals("a", v);
        });
        assertThrows(JsonException.class, () -> Nodes.getInArray(set, 0));
    }

    @Test
    public void setSupportsArrayAddition() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");

        Nodes.addInArray(set, "b");
        assertEquals(2, Nodes.sizeInArray(set));
        assertEquals("[\"a\",\"b\"]", Sjf4j.global().toJsonString(set));

        assertThrows(JsonException.class, () -> Nodes.removeInArray(set, 0));
    }

    @Test
    public void setRoundTripsThroughJson() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");
        set.add("b");

        String json = Sjf4j.global().toJsonString(set);
        assertEquals("[\"a\",\"b\"]", json);

        Set<String> parsed = Sjf4j.global().fromJson(json, new TypeReference<Set<String>>() {});
        assertEquals(set, parsed);
    }

    @Test
    public void setConvertsToNodeAndRawArray() {
        Set<String> set = new LinkedHashSet<>();
        set.add("a");
        Object node = Sjf4j.global().deepNode(set);
        assertInstanceOf(Set.class, node);
        assertEquals("[\"a\"]", Sjf4j.global().toJsonString(node));

        Object node2 = Sjf4j.global().toRaw(set);
        assertInstanceOf(List.class, node2);
    }

}
