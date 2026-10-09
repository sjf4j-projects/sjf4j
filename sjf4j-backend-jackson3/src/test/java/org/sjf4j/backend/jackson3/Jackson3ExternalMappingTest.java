package org.sjf4j.backend.jackson3;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.NodeMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Jackson3ExternalMappingTest {
    static class Person {
        public String name;
        public int age;
    }

    @Test
    void readsAndBuildsNativeTrees() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Ada");
        source.put("age", 32);
        source.put("items", Arrays.asList(1, null, 3));
        ObjectNode node = (ObjectNode) NodeMapper.convert(source, JsonNode.class, false);

        assertEquals("Ada", node.get("name").stringValue());
        assertEquals(32, node.get("age").intValue());
        assertTrue(node.get("items").get(1).isNull());
        Person person = (Person) NodeMapper.convert(node, Person.class, false);
        assertEquals("Ada", person.name);
        assertEquals(32, person.age);

        Map<String, Object> raw = (Map<String, Object>) NodeMapper.convertToRaw(node, RuntimeContext.EMPTY);
        assertEquals(Arrays.asList(1, null, 3), raw.get("items"));
        assertSame(NullNode.instance, NodeMapper.convert(null, JsonNode.class, false));
        assertThrows(BindingException.class, () -> NodeMapper.convert(source, StringNode.class, false));
    }

    @Test
    void convertsJavaArraysToNativeArrays() {
        ArrayNode node = (ArrayNode) NodeMapper.convert(new int[]{1, 2}, JsonNode.class, false);
        assertEquals(2, node.size());
        assertEquals(1, node.get(0).intValue());
        List<Integer> list = (List<Integer>) NodeMapper.convert(node,
                new org.sjf4j.TypeReference<List<Integer>>() {}.getType(), false);
        assertEquals(Arrays.asList(1, 2), list);
    }
}
