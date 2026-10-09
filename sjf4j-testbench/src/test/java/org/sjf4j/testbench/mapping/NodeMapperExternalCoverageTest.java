package org.sjf4j.testbench.mapping;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.NodeMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs in testbench because its JaCoCo report aggregates instrumented core
 * executions, whereas individual backend test executions are not included.
 */
@SuppressWarnings("unchecked")
class NodeMapperExternalCoverageTest {

    @Test
    void createsExternalScalarNodesWithStrictRootAndNestedNullSemantics() {
        assertNull(NodeMapper.convert(null, com.fasterxml.jackson.databind.JsonNode.class, false));
        assertNull(NodeMapper.convert(null, tools.jackson.databind.JsonNode.class, false));
        assertNull(NodeMapper.convert(null, JsonElement.class, false));
        assertNull(NodeMapper.convert(null, jakarta.json.JsonValue.class, false));

        com.fasterxml.jackson.databind.JsonNode j2 = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert("text", com.fasterxml.jackson.databind.JsonNode.class, false);
        assertEquals("text", j2.textValue());
        assertTrue(((com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(true, com.fasterxml.jackson.databind.JsonNode.class, false)).booleanValue());
        assertEquals(7, ((com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(7, com.fasterxml.jackson.databind.JsonNode.class, false)).intValue());
        assertEquals("A", ((com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert('A', com.fasterxml.jackson.databind.JsonNode.class, false)).textValue());
        assertEquals("READY", ((com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(State.READY, com.fasterxml.jackson.databind.JsonNode.class, false)).textValue());

        tools.jackson.databind.JsonNode j3 = (tools.jackson.databind.JsonNode)
                NodeMapper.convert(new BigDecimal("0.00120"), tools.jackson.databind.JsonNode.class, false);
        assertEquals(0, new BigDecimal("0.00120").compareTo(j3.decimalValue()));

        assertEquals("x", ((JsonElement) NodeMapper.convert("x", JsonElement.class, false)).getAsString());
        assertTrue(((JsonElement) NodeMapper.convert(false, JsonElement.class, false))
                .getAsJsonPrimitive().isBoolean());
        assertThrows(BindingException.class, () -> NodeMapper.convert("string",
                com.fasterxml.jackson.databind.node.ObjectNode.class, false));

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("nothing", null);
        nested.put("values", Arrays.asList(null, true));
        com.fasterxml.jackson.databind.JsonNode tree = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(nested, com.fasterxml.jackson.databind.JsonNode.class, false);
        assertTrue(tree.get("nothing").isNull());
        assertTrue(tree.get("values").get(0).isNull());
        assertTrue(tree.get("values").get(1).booleanValue());
    }

    @Test
    void buildsExternalObjectTreesFromMapJsonObjectPojoAndJojo() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("items", Arrays.asList(1, null, "three"));
        map.put("active", true);
        com.fasterxml.jackson.databind.JsonNode j2 = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(map, com.fasterxml.jackson.databind.JsonNode.class, false);
        assertEquals(3, j2.get("items").size());
        assertTrue(j2.get("items").get(1).isNull());

        JsonObject dynamic = JsonObject.of("name", "Ada", "null", null);
        tools.jackson.databind.JsonNode j3 = (tools.jackson.databind.JsonNode)
                NodeMapper.convert(dynamic, tools.jackson.databind.JsonNode.class, false);
        assertEquals("Ada", j3.get("name").stringValue());
        assertTrue(j3.get("null").isNull());

        Profile pojo = profile();
        JsonElement gson = (JsonElement) NodeMapper.convert(pojo, JsonElement.class, false);
        assertEquals("Ada", gson.getAsJsonObject().get("name").getAsString());
        assertEquals("READY", gson.getAsJsonObject().get("state").getAsString());
        assertEquals("codec", gson.getAsJsonObject().get("code").getAsString());
        assertTrue(gson.getAsJsonObject().get("nullable").isJsonNull());

        DynamicPojo jojo = new DynamicPojo();
        jojo.name = "static";
        jojo.put("extra", JsonArray.of(1, 2));
        com.fasterxml.jackson.databind.JsonNode nativeJojo = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(jojo, com.fasterxml.jackson.databind.JsonNode.class, false);
        assertEquals("static", nativeJojo.get("name").textValue());
        assertEquals(2, nativeJojo.get("extra").size());
    }

    @Test
    void buildsExternalArraysFromAllSupportedJavaSequenceShapes() {
        com.fasterxml.jackson.databind.JsonNode list = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(Arrays.asList("a", null, 2),
                        com.fasterxml.jackson.databind.JsonNode.class, false);
        assertTrue(list.isArray());
        assertTrue(list.get(1).isNull());

        com.fasterxml.jackson.databind.JsonNode jsonArray = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(JsonArray.of("a", 2),
                        com.fasterxml.jackson.databind.JsonNode.class, false);
        assertEquals(2, jsonArray.size());

        tools.jackson.databind.JsonNode primitiveArray = (tools.jackson.databind.JsonNode)
                NodeMapper.convert(new int[]{1, 2, 3}, tools.jackson.databind.JsonNode.class, false);
        assertEquals(3, primitiveArray.size());
        assertEquals(3, primitiveArray.get(2).intValue());

        JsonElement set = (JsonElement) NodeMapper.convert(
                new LinkedHashSet<>(Arrays.asList("a", "b")), JsonElement.class, false);
        assertEquals("a", set.getAsJsonArray().get(0).getAsString());
        assertEquals("b", set.getAsJsonArray().get(1).getAsString());
    }

    @Test
    void readsNativeScalarAndNullNodesAsJavaScalarsAndRawValues() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals("text", NodeMapper.convert(mapper.getNodeFactory().textNode("text"), String.class, false));
        assertEquals(12, NodeMapper.convert(mapper.getNodeFactory().numberNode(12), Integer.class, false));
        assertEquals(true, NodeMapper.convert(mapper.getNodeFactory().booleanNode(true), Boolean.class, false));
        assertNull(NodeMapper.convert(com.fasterxml.jackson.databind.node.NullNode.instance,
                String.class, false));

        assertEquals("text", NodeMapper.convertToRaw(mapper.getNodeFactory().textNode("text"), RuntimeContext.EMPTY));
        assertEquals(12, ((Number) NodeMapper.convertToRaw(
                mapper.getNodeFactory().numberNode(12), RuntimeContext.EMPTY)).intValue());
        assertEquals(true, NodeMapper.convertToRaw(
                mapper.getNodeFactory().booleanNode(true), RuntimeContext.EMPTY));
        assertNull(NodeMapper.convertToRaw(com.fasterxml.jackson.databind.node.NullNode.instance,
                RuntimeContext.EMPTY));

        assertEquals("value", NodeMapper.convert(JsonParser.parseString("\"value\""), String.class, false));
        assertEquals(42, ((Number) NodeMapper.convert(
                JsonParser.parseString("42"), Number.class, false)).intValue());
        assertEquals(false, NodeMapper.convert(JsonParser.parseString("false"), Boolean.class, false));
        assertNull(NodeMapper.convert(JsonNull.INSTANCE, String.class, false));
    }

    @Test
    void readsExternalObjectAndArrayIntoTypedPojoCreatorAndStandardContainers() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode nativePojo = mapper.createObjectNode()
                .put("name", "Ada").put("state", "READY").put("code", "raw")
                .set("scores", mapper.createArrayNode().add(3).add(5));
        Profile profile = (Profile) NodeMapper.convert(nativePojo, Profile.class, false);
        assertEquals("Ada", profile.name);
        assertEquals(State.READY, profile.state);
        assertEquals("raw", profile.code.raw);
        assertEquals(Arrays.asList(3, 5), profile.scores);

        com.fasterxml.jackson.databind.JsonNode nativeCreator = mapper.createObjectNode()
                .put("code", "value").put("age", 21);
        Immutable fromCreator = (Immutable) NodeMapper.convert(nativeCreator, Immutable.class, false);
        assertEquals("value", fromCreator.code.raw);
        assertEquals(21, fromCreator.age);

        com.fasterxml.jackson.databind.JsonNode array = mapper.createArrayNode()
                .add(mapper.createObjectNode().put("code", "a").put("age", 1))
                .add(mapper.createObjectNode().put("code", "b").put("age", 2));

        List<Immutable> typed = (List<Immutable>) NodeMapper.convert(
                array, new TypeReference<List<Immutable>>() {}.getType(), false);
        assertEquals(2, typed.size());
        assertEquals("b", typed.get(1).code.raw);

        Immutable[] typedArray = (Immutable[]) NodeMapper.convert(array, Immutable[].class, false);
        assertEquals(1, typedArray[0].age);

        JsonArray sjfArray = (JsonArray) NodeMapper.convert(array, JsonArray.class, false);
        assertEquals("a", ((Map<?, ?>) sjfArray.getNode(0)).get("code"));
        assertEquals(2, ((List<?>) NodeMapper.convert(array, List.class, false)).size());
        assertEquals(2, ((LinkedHashSet<?>) NodeMapper.convert(
                mapper.createArrayNode().add(1).add(2), LinkedHashSet.class, false)).size());

        JsonObject sjfObject = (JsonObject) NodeMapper.convert(nativePojo, JsonObject.class, false);
        assertEquals("Ada", sjfObject.get("name"));
        Map<String, Object> rawMap = (Map<String, Object>) NodeMapper.convert(nativePojo, Map.class, false);
        assertEquals(Arrays.asList(3, 5), rawMap.get("scores"));
        assertTrue(rawMap.get("scores") instanceof List);
    }

    @Test
    void readsNativeOneOfAndReportsInvalidNestedScalarPaths() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode nativeDog =
                mapper.createObjectNode().put("kind", "dog").put("name", "Rex");
        Pet pet = (Pet) NodeMapper.convert(nativeDog, Pet.class, false);
        assertEquals("Rex", assertInstanceOf(Dog.class, pet).name);

        BindingException invalid = assertThrows(BindingException.class, () ->
                NodeMapper.convert(mapper.createArrayNode().add(1).add(true),
                        new TypeReference<List<Integer>>() {}.getType(), false));
        assertTrue(invalid.getMessage().contains("$[1]"));
    }

    @Test
    void convertsNativeTreesAcrossBackendsIncludingScalarNullAndArrays() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode source = mapper.createObjectNode()
                .put("title", "x").put("flag", true).put("amount", new BigDecimal("12.50"))
                .set("array", mapper.createArrayNode().add(1).addNull().add("three"));
        JsonElement gson = (JsonElement) NodeMapper.convert(source, JsonElement.class, false);
        tools.jackson.databind.JsonNode j3 = (tools.jackson.databind.JsonNode)
                NodeMapper.convert(gson, tools.jackson.databind.JsonNode.class, false);
        assertEquals("x", j3.get("title").stringValue());
        assertTrue(j3.get("flag").booleanValue());
        assertTrue(j3.get("array").get(1).isNull());

        JsonElement scalar = (JsonElement) NodeMapper.convert(
                mapper.getNodeFactory().numberNode(9), JsonElement.class, false);
        assertEquals(9, scalar.getAsInt());
        assertTrue(((JsonElement) NodeMapper.convert(
                mapper.getNodeFactory().booleanNode(true), JsonElement.class, false)).getAsBoolean());
        assertEquals("a", ((JsonElement) NodeMapper.convert(
                mapper.getNodeFactory().textNode("a"), JsonElement.class, false)).getAsString());
        assertTrue(((JsonElement) NodeMapper.convert(
                com.fasterxml.jackson.databind.node.NullNode.instance,
                JsonElement.class, false)).isJsonNull());

        JsonElement gsonArray = (JsonElement) NodeMapper.convert(
                mapper.createArrayNode().add(1).add(2), JsonElement.class, false);
        assertEquals(2, gsonArray.getAsJsonArray().size());

        tools.jackson.databind.JsonNode fromGsonNull = (tools.jackson.databind.JsonNode)
                NodeMapper.convert(JsonNull.INSTANCE, tools.jackson.databind.JsonNode.class, false);
        assertTrue(fromGsonNull.isNull());
    }

    @Test
    void preservesOrCopiesNativeSubtreesDependingOnDeepCopy() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode child = mapper.createObjectNode().put("n", 1);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("child", child);
        com.fasterxml.jackson.databind.JsonNode retained = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(source, com.fasterxml.jackson.databind.JsonNode.class, false);
        com.fasterxml.jackson.databind.JsonNode copied = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(source, com.fasterxml.jackson.databind.JsonNode.class, true);
        assertSame(child, retained.get("child"));
        assertNotSame(child, copied.get("child"));
        assertSame(child, NodeMapper.convert(child, com.fasterxml.jackson.databind.JsonNode.class, false));
        assertNotSame(child, NodeMapper.convert(child, com.fasterxml.jackson.databind.JsonNode.class, true));
    }

    @Test
    void convertsJsonpSourceAndLeavesTargetConstructionUnsupported() {
        jakarta.json.JsonObject source = Json.createObjectBuilder()
                .add("name", "Ada")
                .add("flag", true)
                .add("items", Json.createArrayBuilder().add("x").addNull())
                .build();
        Map<String, Object> javaMap = (Map<String, Object>) NodeMapper.convert(source, Map.class, false);
        assertFalse(javaMap instanceof jakarta.json.JsonValue);
        assertEquals(Arrays.asList("x", null), javaMap.get("items"));

        com.fasterxml.jackson.databind.JsonNode j2 = (com.fasterxml.jackson.databind.JsonNode)
                NodeMapper.convert(source, com.fasterxml.jackson.databind.JsonNode.class, false);
        assertEquals("x", j2.get("items").get(0).textValue());

        jakarta.json.JsonArray arr = Json.createArrayBuilder().add(1).addNull().build();
        List<Object> javaList = (List<Object>) NodeMapper.convert(arr, List.class, false);
        assertEquals(Arrays.asList(1, null), javaList);
        assertNull(NodeMapper.convert(null, jakarta.json.JsonValue.class, false));
        assertThrows(BindingException.class, () -> NodeMapper.convert(
                Arrays.asList(1, 2), jakarta.json.JsonValue.class, false));
    }

    @Test
    void rawConversionExpandsNativeTreesAndDetachesNestedCollections() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode source = mapper.createObjectNode()
                .set("a", mapper.createArrayNode().add(mapper.createObjectNode().put("b", 1)));
        Map<String, Object> raw = (Map<String, Object>) NodeMapper.convertToRaw(source, RuntimeContext.EMPTY);
        List<Object> list = (List<Object>) raw.get("a");
        assertEquals(1, ((Number) ((Map<?, ?>) list.get(0)).get("b")).intValue());
        list.add("mutated");
        assertEquals(1, source.get("a").size());

        assertEquals(Arrays.asList(1, 2), NodeMapper.convertToRaw(
                mapper.createArrayNode().add(1).add(2), RuntimeContext.EMPTY));
        assertEquals(Arrays.asList(1, 2), NodeMapper.convertToRaw(
                new int[]{1, 2}, RuntimeContext.EMPTY));
        assertEquals("READY", NodeMapper.convertToRaw(State.READY, RuntimeContext.EMPTY));
        assertEquals("raw", NodeMapper.convertToRaw(new Encoded("raw"), RuntimeContext.EMPTY));
    }

    private static Profile profile() {
        Profile pojo = new Profile();
        pojo.name = "Ada";
        pojo.state = State.READY;
        pojo.code = new Encoded("codec");
        pojo.scores = Arrays.asList(3, 5);
        return pojo;
    }

    enum State { READY }

    @NodeValue
    static class Encoded {
        final String raw;
        Encoded(String raw) { this.raw = raw; }
        @ValueToRaw String encode() { return raw; }
        @RawToValue static Encoded decode(String raw) { return new Encoded(raw); }
    }

    static class Profile {
        public String name;
        public State state;
        public Encoded code;
        public List<Integer> scores;
        public Object nullable;
    }

    static class DynamicPojo extends JsonObject {
        public String name;
    }

    static class Immutable {
        public final Encoded code;
        public final int age;
        @NodeCreator
        public Immutable(@NodeProperty("code") Encoded code, @NodeProperty("age") int age) {
            this.code = code;
            this.age = age;
        }
    }

    @OneOf(key = "kind", value = {
            @OneOf.Mapping(value = Dog.class, when = "dog")
    })
    interface Pet {}
    static class Dog implements Pet {
        public String kind;
        public String name;
    }
}
