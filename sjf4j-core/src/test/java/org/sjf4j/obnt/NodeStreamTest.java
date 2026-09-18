package org.sjf4j.obnt;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeStream;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class NodeStreamTest {

    @Test
    public void testFind() {
        String json1 = "{\n" +
                "  \"book\": [\n" +
                "    { \"title\": \"A\", \"price\": 10, \"tags\": [\"classic\"] },\n" +
                "    { \"title\": \"B\", \"price\": null, \"tags\": [] },\n" +
                "    { \"title\": \"C\", \"isbn.number\": \"123\", \"tags\": null }\n" +
                "  ],\n" +
                "  \"emptyArray\": [],\n" +
                "  \"emptyObject\": {},\n" +
                "  \"nullValue\": null,\n" +
                "  \"weird.keys\": { \"key with spaces\": \"v1\" }\n" +
                "}";
        JsonObject jo1 = JsonObject.fromJson(json1);
        NodeStream<JsonObject> js = NodeStream.of(jo1);
        String[] abc = js.findAsByPath("$.book.*", JsonObject.class)
                .filter(n -> n.hasNonNull("tags"))
                .asByPath("$.title", String.class)
                .toList().toArray(new String[0]);
        assertArrayEquals(new String[]{"A", "B"}, abc);
    }


    @Test
    public void testFind2() {
        String json1 = "{\n" +
                "  \"book\": [\n" +
                "    { \"title\": \"A\", \"price\": 10, \"tags\": [\"classic\"] },\n" +
                "    { \"title\": \"B\", \"price\": null, \"tags\": [] },\n" +
                "    { \"title\": \"C\", \"isbn.number\": \"123\", \"tags\": null }\n" +
                "  ],\n" +
                "  \"emptyArray\": [],\n" +
                "  \"emptyObject\": {},\n" +
                "  \"nullValue\": null,\n" +
                "  \"price\": 88,\n" +
                "  \"weird.keys\": { \"key with spaces\": \"v1\" }\n" +
                "}";
        List<Integer> prices = JsonObject.fromJson(json1).stream()
                .findByPath("$..price", Integer.class)
                .filter(Objects::nonNull)
                .toList();

        assertEquals(2, prices.size());
        assertEquals(10, prices.get(1));

        int priceSum = JsonObject.fromJson(json1).stream()
                .findByPath("$..price", Integer.class)
                .filter(Objects::nonNull)
                .collect(Collectors.summingInt(x -> x));
        assertEquals(98, priceSum);
    }

    private List<JsonObject> sampleNodes() {
        return Arrays.asList(
                JsonObject.of("id", 2, "idText", "2", "name", "beta", "tags", JsonArray.of("x", "y")),
                JsonObject.of("id", 1, "idText", "1", "name", "alpha", "tags", JsonArray.of("y", "z"))
        );
    }

    @Test
    void testPathOperations() {
        assertEquals(Arrays.asList(2, 1), NodeStream.of(sampleNodes()).getByPath("$.id", Integer.class).toList());
        assertEquals(Arrays.asList(2, 1), NodeStream.of(sampleNodes()).asByPath("$.idText", Integer.class).toList());
        assertEquals(Arrays.asList("x", "y", "y", "z"), NodeStream.of(sampleNodes()).findByPath("$.tags[*]", String.class).toList());
        assertEquals(Arrays.asList(2, 1), NodeStream.of(sampleNodes()).evalAsByPath("$.idText", Integer.class).toList());
        assertEquals(Arrays.asList("x", "y", "y", "z"), NodeStream.of(sampleNodes()).evalByPath("$.tags[*]", String.class).toList());
    }

    @Test
    void testJavaStreamWrappers() {
        AtomicInteger peeked = new AtomicInteger();
        List<String> names = NodeStream.of(sampleNodes()).peek(node -> peeked.incrementAndGet())
                .filter(node -> node.getInt("id") > 0)
                .sorted((left, right) -> left.getString("name").compareTo(right.getString("name")))
                .map(node -> node.getString("name")).distinct().skip(0).limit(2).toList();
        assertEquals(Arrays.asList("alpha", "beta"), names);
        assertEquals(2, peeked.get());
        JsonArray tags = NodeStream.of(sampleNodes()).flatMap(node -> node.getJsonArray("tags").toList().stream()).toJsonArray();
        assertEquals(JsonArray.of("x", "y", "y", "z"), tags);
        assertEquals(2, NodeStream.of(sampleNodes()).count());
        assertTrue(NodeStream.of(sampleNodes()).anyMatch(node -> node.getInt("id") == 2));
        assertTrue(NodeStream.of(sampleNodes()).allMatch(node -> node.containsKey("name")));
        assertFalse(NodeStream.of(sampleNodes()).noneMatch(node -> node.containsKey("name")));
        assertEquals("beta", NodeStream.of(sampleNodes()).findFirst().get().getString("name"));
        assertTrue(NodeStream.of(sampleNodes()).findAny().isPresent());
        assertEquals("beta,alpha", NodeStream.of(sampleNodes()).map(node -> node.getString("name")).collect(Collectors.joining(",")));
        assertEquals(Arrays.asList(1, 2, 3), NodeStream.of(Arrays.asList(1, 2, 3)).toList());
        assertEquals(Arrays.asList("a", "b"), NodeStream.of(Stream.of("a", "b").collect(Collectors.toList())).toList());
    }

}
