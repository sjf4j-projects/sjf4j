package org.sjf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.facade.simple.SimpleJsonFacade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sjf4jApiSurfaceTest {

    private Sjf4j sjf4j;

    @BeforeEach
    void setUp() {
        sjf4j = Sjf4j.builder().jsonFacadeProvider(SimpleJsonFacade.provider()).build();
    }

    static class Person {
        public String name;
        public int age;
    }

    @Test
    void testSjf4jApiSurface() {
        String json = "{\"name\":\"Alice\",\"age\":30,\"tags\":[\"x\",\"y\"]}";
        JsonObject fromString = sjf4j.fromJson(json, JsonObject.class);
        JsonObject fromReader = sjf4j.fromJson(new StringReader(json), JsonObject.class);
        JsonObject fromStream = sjf4j.fromJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), JsonObject.class);
        JsonObject fromBytes = sjf4j.fromJson(json.getBytes(StandardCharsets.UTF_8), JsonObject.class);
        assertEquals(fromString, fromReader);
        assertEquals(fromString, fromStream);
        assertEquals(fromString, fromBytes);
        assertTrue(JsonType.of(sjf4j.fromJson(json)).isObject());

        List<Integer> intsFromReader = sjf4j.fromJson(new StringReader("[1,2,3]"), new TypeReference<List<Integer>>() {});
        List<Integer> intsFromString = sjf4j.fromJson("[1,2,3]", new TypeReference<List<Integer>>() {});
        List<Integer> intsFromBytes = sjf4j.fromJson("[1,2,3]".getBytes(StandardCharsets.UTF_8), new TypeReference<List<Integer>>() {});
        List<Integer> intsFromStream = sjf4j.fromJson(new ByteArrayInputStream("[1,2,3]".getBytes(StandardCharsets.UTF_8)), new TypeReference<List<Integer>>() {});
        assertEquals(Arrays.asList(1, 2, 3), intsFromReader);
        assertEquals(intsFromReader, intsFromString);
        assertEquals(intsFromReader, intsFromBytes);
        assertEquals(intsFromReader, intsFromStream);

        StringWriter jsonWriter = new StringWriter();
        sjf4j.toJson(jsonWriter, fromString);
        ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
        sjf4j.toJson(jsonOutput, fromString);
        assertEquals(json, jsonWriter.toString());
        assertEquals(json, new String(jsonOutput.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(json, sjf4j.toJsonString(fromString));
        assertEquals(json, new String(sjf4j.toJsonBytes(fromString), StandardCharsets.UTF_8));

        String yaml = "name: Alice\nage: 30\n";
        JsonObject yamlObject = sjf4j.fromYaml(yaml, JsonObject.class);
        assertEquals("Alice", yamlObject.getString("name"));
        assertEquals(yamlObject, sjf4j.fromYaml(new StringReader(yaml), JsonObject.class));
        assertTrue(JsonType.of(sjf4j.fromYaml(new StringReader(yaml))).isObject());
        assertTrue(JsonType.of(sjf4j.fromYaml(yaml)).isObject());
        Map<String, Object> yamlMap = sjf4j.fromYaml(yaml, new TypeReference<Map<String, Object>>() {});
        assertEquals("Alice", yamlMap.get("name"));
        assertEquals(yamlMap, sjf4j.fromYaml(new StringReader(yaml), new TypeReference<Map<String, Object>>() {}));
        StringWriter yamlWriter = new StringWriter();
        sjf4j.toYaml(yamlWriter, yamlObject);
        assertTrue(yamlWriter.toString().contains("name: Alice"));
        assertTrue(sjf4j.toYamlString(yamlObject).contains("age: 30"));
        assertTrue(new String(sjf4j.toYamlBytes(yamlObject), StandardCharsets.UTF_8).contains("name: Alice"));

        List<Integer> fromNode = sjf4j.fromNode(JsonArray.of(1, 2, 3), new TypeReference<List<Integer>>() {});
        assertEquals(Arrays.asList(1, 2, 3), fromNode);
        Person person = sjf4j.fromNode(JsonObject.of("name", "Alice", "age", 30), Person.class);
        assertEquals("Alice", person.name);
        assertEquals(30, person.age);
        JsonObject bindSource = JsonObject.of("nested", JsonObject.of("value", 1));
        Map<String, Object> runtimeBound = sjf4j.bindNode(bindSource, new TypeReference<Map<String, Object>>() {});
        assertSame(bindSource.getNode("nested"), runtimeBound.get("nested"));
        Map<String, Object> runtimeCopied = sjf4j.fromNode(bindSource, new TypeReference<Map<String, Object>>() {});
        assertNotSame(bindSource.getNode("nested"), runtimeCopied.get("nested"));
        JsonObject deepSource = JsonObject.of("nested", JsonObject.of("value", 1));
        JsonObject deepCopy = sjf4j.deepNode(deepSource);
        deepSource.getJsonObject("nested").put("value", 2);
        assertEquals(1, deepCopy.getIntByPath("$.nested.value"));
        assertInstanceOf(Map.class, sjf4j.toRaw(deepSource));

        Properties properties = sjf4j.toProperties(JsonObject.of("app", JsonObject.of("name", "sjf4j")));
        assertEquals("sjf4j", properties.getProperty("app.name"));
        assertEquals("sjf4j", ((JsonObject) sjf4j.fromProperties(properties)).getStringByPath("$.app.name"));
        assertEquals("sjf4j", sjf4j.fromProperties(properties, JsonObject.class).getStringByPath("$.app.name"));
        assertEquals("sjf4j", sjf4j.fromProperties(properties, new TypeReference<Map<String, Object>>() {}).get("app") instanceof Map
                ? ((Map<?, ?>) sjf4j.fromProperties(properties, new TypeReference<Map<String, Object>>() {}).get("app")).get("name")
                : null);

        assertThrows(NullPointerException.class, () -> sjf4j.fromJson((String) null, JsonObject.class));
        assertThrows(NullPointerException.class, () -> sjf4j.fromJson(json, (TypeReference<JsonObject>) null));
        assertThrows(NullPointerException.class, () -> sjf4j.fromYaml((String) null, JsonObject.class));
        assertThrows(NullPointerException.class, () -> sjf4j.fromNode(JsonObject.of(), (TypeReference<List<Integer>>) null));
        assertThrows(NullPointerException.class, () -> sjf4j.fromProperties(null));
    }
}
