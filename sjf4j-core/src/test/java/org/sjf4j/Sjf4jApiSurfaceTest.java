package org.sjf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.exception.BindingException;

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
        sjf4j = Sjf4j.builder()
                .jsonBinderProvider(BinderProvider.of(Format.JSON, 0, SimpleJsonBinder::new))
                .build();
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

        BindingException yamlRead = assertThrows(BindingException.class,
                () -> sjf4j.fromYaml("name: Alice\n", JsonObject.class));
        assertTrue(yamlRead.getMessage().contains("YAML reading is unavailable"));
        BindingException yamlWrite = assertThrows(BindingException.class,
                () -> sjf4j.toYamlString(JsonObject.of("name", "Alice")));
        assertTrue(yamlWrite.getMessage().contains("YAML writing is unavailable"));

        List<Integer> fromNode = sjf4j.convert(
                JsonArray.of(1, 2, 3), new TypeReference<List<Integer>>() {}, true);
        assertEquals(Arrays.asList(1, 2, 3), fromNode);
        Person person = sjf4j.convert(JsonObject.of("name", "Alice", "age", 30), Person.class, true);
        assertEquals("Alice", person.name);
        assertEquals(30, person.age);
        JsonObject bindSource = JsonObject.of("nested", JsonObject.of("value", 1));
        Map<String, Object> runtimeBound = sjf4j.convert(
                bindSource, new TypeReference<Map<String, Object>>() {}, false);
        assertSame(bindSource.getNode("nested"), runtimeBound.get("nested"));
        Map<String, Object> runtimeCopied = sjf4j.convert(
                bindSource, new TypeReference<Map<String, Object>>() {}, true);
        assertNotSame(bindSource.getNode("nested"), runtimeCopied.get("nested"));
        JsonObject deepSource = JsonObject.of("nested", JsonObject.of("value", 1));
        JsonObject deepCopy = sjf4j.deepcopy(deepSource);
        deepSource.getJsonObject("nested").put("value", 2);
        assertEquals(1, deepCopy.getIntByPath("$.nested.value"));
        assertInstanceOf(Map.class, sjf4j.convertToRaw(deepSource));

        Properties properties = sjf4j.toProperties(JsonObject.of("app", JsonObject.of("name", "sjf4j")));
        assertEquals("sjf4j", properties.getProperty("app.name"));
        assertEquals("sjf4j", ((JsonObject) sjf4j.fromProperties(properties)).getStringByPath("$.app.name"));
        assertEquals("sjf4j", sjf4j.fromProperties(properties, JsonObject.class).getStringByPath("$.app.name"));
        Map<String, Object> propertiesMap = sjf4j.fromProperties(properties,
                new TypeReference<Map<String, Object>>() {});
        JsonObject nestedProperties = assertInstanceOf(JsonObject.class, propertiesMap.get("app"));
        assertEquals("sjf4j", nestedProperties.getString("name"));

        assertThrows(NullPointerException.class, () -> sjf4j.fromJson((String) null, JsonObject.class));
        assertThrows(NullPointerException.class, () -> sjf4j.fromJson(json, (TypeReference<JsonObject>) null));
        assertThrows(NullPointerException.class, () -> sjf4j.fromYaml((String) null, JsonObject.class));
        assertThrows(NullPointerException.class,
                () -> sjf4j.convert(JsonObject.of(), (TypeReference<List<Integer>>) null, false));
        assertThrows(NullPointerException.class, () -> sjf4j.fromProperties(null));
    }
}
