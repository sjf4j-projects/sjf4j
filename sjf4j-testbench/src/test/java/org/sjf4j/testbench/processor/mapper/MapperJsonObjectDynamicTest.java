package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapperJsonObjectDynamicTest {
    @Test
    public void mapsDynamicJsonObjectChildrenToCompleteTypedGraph() {
        DynamicMapper mapper = CompiledInstances.of(DynamicMapper.class);
        JsonObject source = JsonObject.of(
                "title", "production", "byteValue", 8L, "shortValue", 9L, "count", 7L, "total", 10L,
                "ratio", 1.25D, "score", 2.5D, "initial", "Z", "active", true,
                "owner", JsonObject.of("id", 1L, "name", "owner"),
                "children", JsonArray.of(JsonObject.of("id", 2L, "name", "child")),
                "labels", JsonObject.of("region", "us-east"), "primaryLabel", "primary-production",
                "events", JsonArray.of(JsonObject.of("type", "login", "id", 3L, "attempts", 2),
                        JsonObject.of("type", "comment", "text", "hello", "likes", 4)));

        Graph graph = mapper.map(source);
        assertEquals("production", graph.title);
        assertEquals((byte) 8, graph.byteValue);
        assertEquals((short) 9, graph.shortValue);
        assertEquals(7, graph.count);
        assertEquals(10L, graph.total);
        assertEquals(1.25F, graph.ratio);
        assertEquals(2.5D, graph.score);
        assertEquals('Z', graph.initial);
        assertTrue(graph.active);
        assertEquals(new Child(1L, "owner"), graph.owner);
        assertEquals(List.of(new Child(2L, "child")), graph.children);
        assertEquals("us-east", graph.labels.get("region").value);
        assertEquals("primary-production", graph.primaryLabel.value);
        assertInstanceOf(Login.class, graph.events.get(0));
        Login login = (Login) graph.events.get(0);
        assertEquals(3L, login.id);
        assertEquals(2, login.attempts);
        assertInstanceOf(Comment.class, graph.events.get(1));
        Comment comment = (Comment) graph.events.get(1);
        assertEquals("hello", comment.text);
        assertEquals(4, comment.likes);

        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("region", "eu-west");
        labels.put("unset", null);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "comment");
        event.put("text", "from-map");
        event.put("likes", 5);
        Map<String, Object> loginEvent = new LinkedHashMap<>();
        loginEvent.put("type", "login");
        loginEvent.put("id", 6L);
        loginEvent.put("attempts", 3);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("id", 4L);
        owner.put("name", "map-owner");
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("id", 5L);
        child.put("name", "map-child");
        source.put("labels", labels);
        source.put("event", event);
        source.put("events", Arrays.<Object>asList(loginEvent, event));
        source.put("owner", owner);
        source.put("children", Arrays.<Object>asList(child));

        graph = mapper.map(source);
        assertEquals("eu-west", graph.labels.get("region").value);
        assertTrue(graph.labels.containsKey("unset"));
        assertNull(graph.labels.get("unset"));
        assertInstanceOf(Comment.class, graph.event);
        assertEquals("from-map", ((Comment) graph.event).text);
        assertEquals(5, ((Comment) graph.event).likes);
        assertInstanceOf(Login.class, graph.events.get(0));
        Login mapLogin = (Login) graph.events.get(0);
        assertEquals(6L, mapLogin.id);
        assertEquals(3, mapLogin.attempts);
        assertInstanceOf(Comment.class, graph.events.get(1));
        Comment mapComment = (Comment) graph.events.get(1);
        assertEquals("from-map", mapComment.text);
        assertEquals(5, mapComment.likes);
        assertEquals(new Child(4L, "map-owner"), graph.owner);
        assertEquals(List.of(new Child(5L, "map-child")), graph.children);

        Graph missing = mapper.map(JsonObject.of());
        assertDefaults(missing);
        Graph nulls = mapper.map(JsonObject.of("byteValue", null, "shortValue", null, "count", null,
                "total", null, "ratio", null, "score", null, "initial", null, "active", null,
                "title", null, "owner", null, "children", null, "labels", null, "primaryLabel", null,
                "events", null, "event", null));
        assertDefaults(nulls);
    }

    private static void assertDefaults(Graph graph) {
        assertEquals((byte) 0, graph.byteValue);
        assertEquals((short) 0, graph.shortValue);
        assertEquals(0, graph.count);
        assertEquals(0L, graph.total);
        assertEquals(0F, graph.ratio);
        assertEquals(0D, graph.score);
        assertEquals('\0', graph.initial);
        assertEquals(false, graph.active);
        assertNull(graph.title);
        assertNull(graph.owner);
        assertNull(graph.children);
        assertNull(graph.labels);
        assertNull(graph.primaryLabel);
        assertNull(graph.events);
        assertNull(graph.event);
    }

    @CompiledMapper
    interface DynamicMapper { Graph map(JsonObject source); }

    public static final class Graph {
        public String title;
        public byte byteValue;
        public short shortValue;
        public int count;
        public long total;
        public float ratio;
        public double score;
        public char initial;
        public boolean active;
        public Child owner;
        public List<Child> children;
        public Map<String, Value> labels;
        public Value primaryLabel;
        public List<Event> events;
        public Event event;
    }

    public static final class Child {
        public long id;
        public String name;
        public Child() {}
        Child(long id, String name) { this.id = id; this.name = name; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Child)) return false;
            Child child = (Child) other;
            return id == child.id && (name == null ? child.name == null : name.equals(child.name));
        }
        @Override public int hashCode() { return (int) (31 * id + (name == null ? 0 : name.hashCode())); }
    }

    @NodeValue
    public static final class Value {
        public String value;
        @RawToValue public static Value fromRaw(String raw) { Value value = new Value(); value.value = raw; return value; }
        @ValueToRaw public String toRaw() { return value; }
    }

    @OneOf(key = "type", value = {
            @OneOf.Mapping(value = Login.class, when = "login"),
            @OneOf.Mapping(value = Comment.class, when = "comment")
    })
    interface Event {}

    public static final class Login implements Event { public long id; public int attempts; }
    public static final class Comment implements Event { public String text; public int likes; }
}
