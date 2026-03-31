package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeStreamCoverageTest {

    private List<JsonObject> sampleNodes() {
        return Arrays.asList(
                JsonObject.of(
                        "id", 2,
                        "idText", "2",
                        "name", "beta",
                        "tags", JsonArray.of("x", "y")),
                JsonObject.of(
                        "id", 1,
                        "idText", "1",
                        "name", "alpha",
                        "tags", JsonArray.of("y", "z"))
        );
    }

    @Test
    void testPathOperations() {
        assertEquals(Arrays.asList(2, 1), NodeStream.of(sampleNodes())
                .getByPath("$.id", Integer.class)
                .toList());

        assertEquals(Arrays.asList(2, 1), NodeStream.of(sampleNodes())
                .asByPath("$.idText", Integer.class)
                .toList());

        assertEquals(Arrays.asList("x", "y", "y", "z"), NodeStream.of(sampleNodes())
                .findByPath("$.tags[*]", String.class)
                .toList());

        assertEquals(Arrays.asList(2, 1), NodeStream.of(sampleNodes())
                .evalAsByPath("$.idText", Integer.class)
                .toList());

        assertEquals(Arrays.asList("x", "y", "y", "z"), NodeStream.of(sampleNodes())
                .evalByPath("$.tags[*]", String.class)
                .toList());
    }

    @Test
    void testJavaStreamWrappers() {
        AtomicInteger peeked = new AtomicInteger();

        List<String> names = NodeStream.of(sampleNodes())
                .peek(node -> peeked.incrementAndGet())
                .filter(node -> node.getInt("id") > 0)
                .sorted((left, right) -> left.getString("name").compareTo(right.getString("name")))
                .map(node -> node.getString("name"))
                .distinct()
                .skip(0)
                .limit(2)
                .toList();

        assertEquals(Arrays.asList("alpha", "beta"), names);
        assertEquals(2, peeked.get());

        JsonArray tags = NodeStream.of(sampleNodes())
                .flatMap(node -> node.getJsonArray("tags").toList().stream())
                .toJsonArray();
        assertEquals(JsonArray.of("x", "y", "y", "z"), tags);

        assertEquals(2, NodeStream.of(sampleNodes()).count());
        assertTrue(NodeStream.of(sampleNodes()).anyMatch(node -> node.getInt("id") == 2));
        assertTrue(NodeStream.of(sampleNodes()).allMatch(node -> node.containsKey("name")));
        assertFalse(NodeStream.of(sampleNodes()).noneMatch(node -> node.containsKey("name")));
        assertEquals("beta", NodeStream.of(sampleNodes()).findFirst().get().getString("name"));
        assertTrue(NodeStream.of(sampleNodes()).findAny().isPresent());

        String joined = NodeStream.of(sampleNodes())
                .map(node -> node.getString("name"))
                .collect(Collectors.joining(","));
        assertEquals("beta,alpha", joined);

        assertEquals(Arrays.asList(1, 2, 3), NodeStream.of(Arrays.asList(1, 2, 3)).toList());
        assertEquals(Arrays.asList("a", "b"), NodeStream.of(Stream.of("a", "b").collect(Collectors.toList())).toList());
    }
}
