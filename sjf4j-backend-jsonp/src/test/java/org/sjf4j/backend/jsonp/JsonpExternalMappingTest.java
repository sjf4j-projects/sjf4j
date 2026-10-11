package org.sjf4j.backend.jsonp;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.Nodes;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.NodeMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonpExternalMappingTest {
    @Test
    void nativeNullScalarsFollowNodesNullSemantics() {
        assertNull(Nodes.toChar(JsonValue.NULL));
        assertNull(Nodes.asChar(JsonValue.NULL));
        assertNull(Nodes.toEnum(JsonValue.NULL, Thread.State.class));
        assertNull(Nodes.asEnum(JsonValue.NULL, Thread.State.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void mapsJsonpObjectsWithoutImplicitlyUnwrappingObjectTypedChildren() {
        JsonObject nativeObject = Json.createObjectBuilder()
                .add("a", 1)
                .add("names", Json.createArrayBuilder().add("x").addNull())
                .build();

        Map<String, Object> map = (Map<String, Object>) NodeMapper.convert(
                nativeObject, new TypeReference<Map<String, Object>>() {}.getType(), false);

        // The parameterized Map is reconstructed, but Object-typed children
        // retain their native JSON-P values in non-copy conversion mode.
        assertNotSame(nativeObject, map);
        assertEquals(1, ((JsonNumber) map.get("a")).intValue());
        JsonArray names = (JsonArray) map.get("names");
        assertSame(nativeObject.get("names"), names);
        assertEquals("x", ((JsonString) names.get(0)).getString());
        assertSame(JsonValue.NULL, names.get(1));

        // Explicit raw conversion recursively converts native scalars and null.
        Map<String, Object> raw = (Map<String, Object>)
                NodeMapper.convertToRaw(nativeObject, RuntimeContext.EMPTY);
        assertNotSame(nativeObject, raw);
        assertEquals(1, ((Number) raw.get("a")).intValue());
        assertEquals(Arrays.asList("x", null), raw.get("names"));
    }

    @Test
    void constructsImmutableJsonpObjectsScalarsAndNestedNulls() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Ada");
        source.put("active", true);
        source.put("nothing", null);
        source.put("amount", new BigDecimal("0.00120"));
        source.put("big", new BigInteger("123456789012345678901234567890"));
        source.put("items", Arrays.asList(1, null, "three"));

        JsonObject target = (JsonObject) NodeMapper.convert(source, JsonValue.class, false);
        assertEquals("Ada", target.getString("name"));
        assertTrue(target.getBoolean("active"));
        assertSame(JsonValue.NULL, target.get("nothing"));
        assertEquals(new BigDecimal("0.00120"), target.getJsonNumber("amount").bigDecimalValue());
        assertEquals(new BigInteger("123456789012345678901234567890"),
                target.getJsonNumber("big").bigIntegerValue());
        assertSame(JsonValue.NULL, target.getJsonArray("items").get(1));
        assertEquals("three", target.getJsonArray("items").getString(2));

        // JsonValue is the external target; Map/List subinterfaces remain
        // governed by the standard Java container precedence.
        assertThrows(BindingException.class, () -> NodeMapper.convert(source, JsonObject.class, false));
        assertThrows(BindingException.class, () -> NodeMapper.convert(
                Arrays.asList(1, null), JsonArray.class, false));
        assertNull(NodeMapper.convert(null, JsonValue.class, false));
        assertInstanceOf(JsonString.class, NodeMapper.convert("x", JsonString.class, false));
        assertEquals(7, ((JsonNumber) NodeMapper.convert(7, JsonNumber.class, false)).intValue());
        assertThrows(BindingException.class, () -> NodeMapper.convert("not an object", JsonObject.class, false));
    }

    @Test
    void buildsJsonpArraysFromEverySequenceSource() {
        Object[] sources = {
                Arrays.asList(1, 2, 3),
                org.sjf4j.JsonArray.of(1, 2, 3),
                new int[]{1, 2, 3},
                new Integer[]{1, 2, 3},
                new LinkedHashSet<>(Arrays.asList(1, 2, 3))
        };
        for (Object source : sources) {
            JsonArray array = (JsonArray) NodeMapper.convert(source, JsonValue.class, false);
            assertEquals(3, array.size());
            assertEquals(1, array.getInt(0));
            assertEquals(3, array.getInt(2));
        }
        JsonArray withNull = (JsonArray) NodeMapper.convert(
                new Integer[]{1, null, 3}, JsonValue.class, false);
        assertSame(JsonValue.NULL, withNull.get(1));
    }

    @Test
    void buildsJsonpFromPojoJojoAndValueCodecWithoutMutatingSources() {
        Profile pojo = new Profile();
        pojo.name = "Ada";
        pojo.code = new Encoded("coded");
        pojo.items = Arrays.asList("a", null);

        JsonObject fromPojo = (JsonObject) NodeMapper.convert(pojo, JsonValue.class, false);
        assertEquals("Ada", fromPojo.getString("name"));
        assertEquals("coded", fromPojo.getString("code"));
        assertSame(JsonValue.NULL, fromPojo.getJsonArray("items").get(1));

        org.sjf4j.JsonObject jojo = org.sjf4j.JsonObject.of(
                "static", "value", "dynamic", Arrays.asList(true, false));
        JsonObject fromJojo = (JsonObject) NodeMapper.convert(jojo, JsonValue.class, true);
        assertEquals("value", fromJojo.getString("static"));
        assertTrue(fromJojo.getJsonArray("dynamic").getBoolean(0));

        JsonValue code = (JsonValue) NodeMapper.convert(new Encoded("root"), JsonValue.class, false);
        assertEquals("root", ((JsonString) code).getString());
        assertEquals("coded", pojo.code.value);
    }

    @Test
    void jsonpContainersFollowJavaMapListPrecedence() {
        JsonObject source = Json.createObjectBuilder()
                .add("nested", Json.createArrayBuilder().add(1).addNull()).build();
        JsonArray nativeArray = source.getJsonArray("nested");

        // Assignable targets preserve identity without deep copying.
        assertSame(source, NodeMapper.convert(source, JsonValue.class, false));
        assertSame(source, NodeMapper.convert(source, JsonObject.class, false));
        assertSame(nativeArray, NodeMapper.convert(nativeArray, JsonArray.class, false));

        // JSON-P Map/List implementations are immutable, and no external-adapter
        // fallback is permitted when Java container deep-copy construction fails.
        assertThrows(BindingException.class, () -> NodeMapper.convert(source, JsonValue.class, true));
        assertThrows(BindingException.class, () -> NodeMapper.convert(source, JsonObject.class, true));
        assertThrows(BindingException.class, () -> NodeMapper.convert(nativeArray, JsonArray.class, true));
    }

    @NodeValue
    static class Encoded {
        final String value;
        Encoded(String value) { this.value = value; }
        @ValueToRaw String encode() { return value; }
        @RawToValue static Encoded decode(String raw) { return new Encoded(raw); }
    }

    static class Profile {
        public String name;
        public Encoded code;
        public List<String> items;
    }

    @SuppressWarnings("unchecked")
    @Test
    void reusesJsonpMapAndListViewsWhenTargetIsAssignable() {
        JsonObject nativeObject = Json.createObjectBuilder()
                .add("a", 1)
                .add("items", Json.createArrayBuilder().addNull().add("x"))
                .build();

        // Map/List-first, no deep copy: preserve the source instance.
        Map<String, Object> map = (Map<String, Object>)
                NodeMapper.convert(nativeObject, Map.class, false);
        assertSame(nativeObject, map);
        assertEquals(1, ((JsonNumber) map.get("a")).intValue());
        assertSame(nativeObject.get("items"), map.get("items"));

        JsonArray nativeArray = Json.createArrayBuilder()
                .add(Json.createObjectBuilder().add("id", 1))
                .addNull().build();
        List<?> list = (List<?>) NodeMapper.convert(nativeArray, List.class, false);
        assertSame(nativeArray, list);
        assertEquals(1, ((JsonNumber) ((JsonObject) list.get(0)).get("id")).intValue());
        assertSame(JsonValue.NULL, list.get(1));

        // Detached raw values are an explicit separate operation.
        Map<String, Object> rawMap = (Map<String, Object>)
                NodeMapper.convertToRaw(nativeObject, RuntimeContext.EMPTY);
        assertFalse(rawMap instanceof JsonValue);
        assertEquals(1, ((Number) rawMap.get("a")).intValue());
        assertEquals(Arrays.asList(null, "x"), rawMap.get("items"));

        List<?> rawList = (List<?>) NodeMapper.convertToRaw(nativeArray, RuntimeContext.EMPTY);
        assertFalse(rawList instanceof JsonValue);
        assertEquals(1, ((Number) ((Map<?, ?>) rawList.get(0)).get("id")).intValue());
        assertNull(rawList.get(1));
    }
}
