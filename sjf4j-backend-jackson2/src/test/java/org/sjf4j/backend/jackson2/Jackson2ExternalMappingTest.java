package org.sjf4j.backend.jackson2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.NodeMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Jackson2ExternalMappingTest {
    static class Person {
        public String name;
        public int age;
    }

    @Test
    void mapsNativeTreeToPojoAndTypedJavaContainers() {
        ObjectNode nativeTree = new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode().put("name", "Ada").put("age", 32);
        Person person = (Person) NodeMapper.convert(nativeTree, Person.class, false);
        assertEquals("Ada", person.name);
        assertEquals(32, person.age);

        Map<String, List<Integer>> typed = (Map<String, List<Integer>>) NodeMapper.convert(
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                        .set("items", new com.fasterxml.jackson.databind.ObjectMapper()
                                .createArrayNode().add(1).add(2)),
                new TypeReference<Map<String, List<Integer>>>() {}.getType(), false);
        assertEquals(Arrays.asList(1, 2), typed.get("items"));
    }

    @Test
    void buildsNativeValuesAndTreesWithoutIntermediateRepresentation() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", Arrays.asList("a", null, 2));
        data.put("amount", new BigDecimal("0.00120"));
        JsonNode converted = (JsonNode) NodeMapper.convert(data, JsonNode.class, false);

        assertTrue(converted.isObject());
        assertEquals("a", converted.get("items").get(0).textValue());
        assertTrue(converted.get("items").get(1).isNull());
        assertEquals(2, converted.get("items").get(2).intValue());
        assertEquals(0, converted.get("amount").decimalValue().compareTo(new BigDecimal("0.00120")));

        Map<String, Object> raw = (Map<String, Object>) NodeMapper.convertToRaw(converted, RuntimeContext.EMPTY);
        assertEquals(Arrays.asList("a", null, 2), raw.get("items"));
        assertEquals(0, new BigDecimal("0.00120")
                .compareTo(new BigDecimal(raw.get("amount").toString())));
        assertSame(NullNode.instance, NodeMapper.convert(null, JsonNode.class, false));
        assertThrows(BindingException.class, () -> NodeMapper.convert(data, TextNode.class, false));
    }

    @NodeValue
    static class Code {
        final String text;
        Code(String text) { this.text = text; }
        @ValueToRaw String encode() { return text; }
        @RawToValue static Code decode(String text) { return new Code(text); }
    }

    static class Holder {
        public Code code;
    }

    @Test
    void convertsValueCodecsThroughExternalProperties() {
        ObjectNode source = new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode().put("code", "hello");
        Holder holder = (Holder) NodeMapper.convert(source, Holder.class, false);
        assertEquals("hello", holder.code.text);
        assertEquals("hello", ((JsonNode) NodeMapper.convert(holder, JsonNode.class, false))
                .get("code").textValue());

        Code direct = (Code) NodeMapper.convert(
                com.fasterxml.jackson.databind.node.TextNode.valueOf("world"), Code.class, false);
        assertEquals("world", direct.text);
    }

    @Test
    void deeplyCopiesExistingNativeSubtreesInsideJavaContainers() {
        ObjectNode child = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("x", 1);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("child", child);
        ObjectNode shallow = (ObjectNode) NodeMapper.convert(map, JsonNode.class, false);
        ObjectNode deep = (ObjectNode) NodeMapper.convert(map, JsonNode.class, true);
        assertSame(child, shallow.get("child"));
        assertNotSame(child, deep.get("child"));
    }
}
