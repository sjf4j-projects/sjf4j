package org.sjf4j.backend.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.NodeMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GsonExternalMappingTest {
    static class Person {
        public String name;
        public int age;
    }

    @Test
    void mapsObjectsScalarsAndNullInBothDirections() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Ada");
        source.put("age", 32);
        source.put("items", Arrays.asList(true, null, "x"));

        JsonObject node = (JsonObject) NodeMapper.convert(source, JsonElement.class, false, RuntimeContext.EMPTY);
        assertEquals("Ada", node.get("name").getAsString());
        assertEquals(32, node.get("age").getAsInt());
        assertSame(JsonNull.INSTANCE, node.getAsJsonArray("items").get(1));

        Person person = (Person) NodeMapper.convert(node, Person.class, false, RuntimeContext.EMPTY);
        assertEquals("Ada", person.name);
        assertEquals(32, person.age);

        Map<?, ?> raw = assertInstanceOf(Map.class, NodeMapper.convertToRaw(node, RuntimeContext.EMPTY));
        assertEquals(Arrays.asList(true, null, "x"), raw.get("items"));
        assertNull(NodeMapper.convert(null, JsonElement.class, false, RuntimeContext.EMPTY));
        assertThrows(BindingException.class, () -> NodeMapper.convert(source, com.google.gson.JsonPrimitive.class, false, RuntimeContext.EMPTY));
    }

    @Test
    void copiesNativeSubtreesWhenDeepCopyIsRequested() {
        JsonObject child = JsonParser.parseString("{\"x\":1}").getAsJsonObject();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("child", child);
        JsonObject shared = (JsonObject) NodeMapper.convert(map, JsonElement.class, false, RuntimeContext.EMPTY);
        JsonObject copied = (JsonObject) NodeMapper.convert(map, JsonElement.class, true, RuntimeContext.EMPTY);
        assertSame(child, shared.get("child"));
        assertNotSame(child, copied.get("child"));
    }
}
