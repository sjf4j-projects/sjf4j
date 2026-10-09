package org.sjf4j.backend.jsonp;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.NodeMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonpExternalMappingTest {
    @Test
    void readsImmutableJsonpObjectsAsJavaNodes() {
        JsonObject nativeObject = Json.createObjectBuilder()
                .add("a", 1)
                .add("names", Json.createArrayBuilder().add("x").addNull())
                .build();
        Map<String, Object> map = (Map<String, Object>) NodeMapper.convert(
                nativeObject, new TypeReference<Map<String, Object>>() {}.getType(), false);
        assertEquals(1, ((Number) map.get("a")).intValue());
        assertEquals("x", ((List<?>) map.get("names")).get(0));
        assertNull(((List<?>) map.get("names")).get(1));
        assertEquals(map, NodeMapper.convertToRaw(nativeObject, RuntimeContext.EMPTY));
    }

    @Test
    void refusesImmutableJsonpTargetConstruction() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Ada");
        assertThrows(BindingException.class, () -> NodeMapper.convert(source, JsonValue.class, false));
        assertThrows(BindingException.class, () -> NodeMapper.convert(null, JsonValue.class, false));
    }
}
