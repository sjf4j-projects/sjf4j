package org.sjf4j.backend.jsonp;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.NodeMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonpExternalMappingTest {
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
    void refusesImmutableJsonpTargetConstruction() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Ada");
        assertThrows(BindingException.class, () -> NodeMapper.convert(source, JsonValue.class, false));
        assertNull(NodeMapper.convert(null, JsonValue.class, false));
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
