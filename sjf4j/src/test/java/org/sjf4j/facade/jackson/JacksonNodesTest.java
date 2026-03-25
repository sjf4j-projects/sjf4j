package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonNodesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testKindsAndScalarConversions() {
        ObjectNode objectNode = MAPPER.createObjectNode().put("name", "han");
        ArrayNode arrayNode = MAPPER.createArrayNode().add(1).add("x");

        assertTrue(JacksonNodes.isNode(objectNode));
        assertTrue(JacksonNodes.isNode(objectNode.getClass()));
        assertFalse(JacksonNodes.isNode("x"));
        assertFalse(JacksonNodes.isNode(String.class));

        assertEquals(NodeKind.VALUE_NULL, JacksonNodes.kindOf(JsonNodeFactory.instance.nullNode()));
        assertEquals(NodeKind.VALUE_STRING_FACADE, JacksonNodes.kindOf(TextNode.valueOf("x")));
        assertEquals(NodeKind.VALUE_NUMBER_FACADE, JacksonNodes.kindOf(JsonNodeFactory.instance.numberNode(1)));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, JacksonNodes.kindOf(BooleanNode.TRUE));
        assertEquals(NodeKind.OBJECT_FACADE, JacksonNodes.kindOf(objectNode));
        assertEquals(NodeKind.ARRAY_FACADE, JacksonNodes.kindOf(arrayNode));
        assertEquals(NodeKind.OBJECT_FACADE, JacksonNodes.kindOf(ObjectNode.class));
        assertEquals(NodeKind.ARRAY_FACADE, JacksonNodes.kindOf(ArrayNode.class));
        assertEquals(NodeKind.VALUE_STRING_FACADE, JacksonNodes.kindOf(TextNode.class));
        assertEquals(NodeKind.VALUE_NUMBER_FACADE, JacksonNodes.kindOf(JsonNodeFactory.instance.numberNode(1).getClass()));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, JacksonNodes.kindOf(BooleanNode.class));
        assertEquals(NodeKind.UNKNOWN, JacksonNodes.kindOf(com.fasterxml.jackson.databind.JsonNode.class));
        assertEquals(NodeKind.UNKNOWN, JacksonNodes.kindOf(new BinaryNode(new byte[]{1})));
        assertThrows(JsonException.class, () -> JacksonNodes.kindOf(new POJONode("x")));
        assertThrows(JsonException.class, () -> JacksonNodes.kindOf("x"));
        assertThrows(JsonException.class, () -> JacksonNodes.kindOf(String.class));

        assertEquals("x", JacksonNodes.toString(TextNode.valueOf("x")));
        assertEquals("1", JacksonNodes.asString(JsonNodeFactory.instance.numberNode(1)));
        assertEquals(1, JacksonNodes.toNumber(JsonNodeFactory.instance.numberNode(1)).intValue());
        assertEquals(2, JacksonNodes.asNumber(TextNode.valueOf("2")).intValue());
        assertNull(JacksonNodes.asNumber(objectNode));
        assertTrue(JacksonNodes.toBoolean(BooleanNode.TRUE));
        assertTrue(JacksonNodes.asBoolean(TextNode.valueOf("true")));
        assertTrue(JacksonNodes.asBoolean(JsonNodeFactory.instance.numberNode(1)));
        assertNull(JacksonNodes.asBoolean(objectNode));

        assertThrows(JsonException.class, () -> JacksonNodes.toString(objectNode));
        assertThrows(JsonException.class, () -> JacksonNodes.toNumber(objectNode));
        assertThrows(JsonException.class, () -> JacksonNodes.toBoolean(objectNode));
    }

    @Test
    void testContainersVisitorsAndUnsupportedMutations() {
        ObjectNode objectNode = MAPPER.createObjectNode();
        objectNode.put("name", "han");
        objectNode.put("age", 18);
        ArrayNode arrayNode = MAPPER.createArrayNode().add("x").add(2).add(false);

        assertEquals("han", JacksonNodes.toJsonObject(objectNode).getString("name"));
        assertEquals(2, JacksonNodes.toMap(objectNode).size());
        assertEquals("x", JacksonNodes.toJsonArray(arrayNode).getString(0));
        assertEquals(3, JacksonNodes.toList(arrayNode).size());
        assertEquals(3, JacksonNodes.toArray(arrayNode).length);
        assertEquals(3, JacksonNodes.toSet(arrayNode).size());
        assertEquals(2, JacksonNodes.sizeInObject(objectNode));
        assertEquals(3, JacksonNodes.sizeInArray(arrayNode));
        assertTrue(JacksonNodes.keySetInObject(objectNode).contains("name"));
        assertTrue(JacksonNodes.entrySetInObject(objectNode).stream().anyMatch(e -> e.getKey().equals("age")));
        assertTrue(JacksonNodes.containsInObject(objectNode, "name"));
        assertEquals(18, JacksonNodes.toNumber(JacksonNodes.getInObject(objectNode, "age")).intValue());
        assertEquals(2, JacksonNodes.toNumber(JacksonNodes.getInArray(arrayNode, 1)).intValue());

        Nodes.Access access = new Nodes.Access();
        JacksonNodes.accessInObject(objectNode, null, "name", access);
        assertNotNull(access.node);
        JacksonNodes.accessInArray(arrayNode, null, 0, access);
        assertNotNull(access.node);

        List<String> keys = new ArrayList<>();
        JacksonNodes.visitObject(objectNode, (key, value) -> keys.add(key));
        assertEquals(2, keys.size());
        assertTrue(JacksonNodes.anyMatchInObject(objectNode, (key, value) -> key.equals("age")));
        assertFalse(JacksonNodes.anyMatchInObject(objectNode, (key, value) -> false));
        assertTrue(JacksonNodes.transformInObject(objectNode, (key, value) -> key.equals("name") ? TextNode.valueOf("jack") : value));
        assertFalse(JacksonNodes.transformInObject(objectNode, (key, value) -> value));

        List<Integer> indexes = new ArrayList<>();
        JacksonNodes.visitArray(arrayNode, (idx, value) -> indexes.add(idx));
        assertEquals(List.of(0, 1, 2), indexes);
        assertTrue(JacksonNodes.anyMatchInArray(arrayNode, (idx, value) -> idx == 1));
        assertFalse(JacksonNodes.anyMatchInArray(arrayNode, (idx, value) -> false));
        assertTrue(JacksonNodes.allMatchInArray(arrayNode, (idx, value) -> idx < 3));
        assertFalse(JacksonNodes.allMatchInArray(arrayNode, (idx, value) -> idx < 2));

        assertThrows(JsonException.class, () -> JacksonNodes.toJsonObject(arrayNode));
        assertThrows(JsonException.class, () -> JacksonNodes.toMap(arrayNode));
        assertThrows(JsonException.class, () -> JacksonNodes.toJsonArray(objectNode));
        assertThrows(JsonException.class, () -> JacksonNodes.toList(objectNode));
        assertThrows(JsonException.class, () -> JacksonNodes.toArray(objectNode));
        assertThrows(JsonException.class, () -> JacksonNodes.toSet(objectNode));
        assertThrows(JsonException.class, () -> JacksonNodes.sizeInObject(arrayNode));
        assertThrows(JsonException.class, () -> JacksonNodes.sizeInArray(objectNode));
        assertThrows(JsonException.class, () -> JacksonNodes.keySetInObject(arrayNode));
        assertThrows(JsonException.class, () -> JacksonNodes.entrySetInObject(arrayNode));
        assertThrows(JsonException.class, () -> JacksonNodes.iteratorInArray(objectNode));
        assertThrows(JsonException.class, () -> JacksonNodes.containsInObject(arrayNode, "name"));
        assertThrows(JsonException.class, () -> JacksonNodes.getInObject(arrayNode, "name"));
        assertThrows(JsonException.class, () -> JacksonNodes.getInArray(objectNode, 0));
        assertThrows(JsonException.class, () -> JacksonNodes.accessInObject(arrayNode, null, "name", new Nodes.Access()));
        assertThrows(JsonException.class, () -> JacksonNodes.accessInArray(objectNode, null, 0, new Nodes.Access()));
        assertThrows(JsonException.class, () -> JacksonNodes.visitObject(arrayNode, (k, v) -> {}));
        assertThrows(JsonException.class, () -> JacksonNodes.anyMatchInObject(arrayNode, (k, v) -> true));
        assertThrows(JsonException.class, () -> JacksonNodes.transformInObject(arrayNode, (k, v) -> v));
        assertThrows(JsonException.class, () -> JacksonNodes.visitArray(objectNode, (i, v) -> {}));
        assertThrows(JsonException.class, () -> JacksonNodes.anyMatchInArray(objectNode, (i, v) -> true));
        assertThrows(JsonException.class, () -> JacksonNodes.allMatchInArray(objectNode, (i, v) -> true));
        assertThrows(JsonException.class, () -> JacksonNodes.putInObject(objectNode, "x", TextNode.valueOf("y")));
        assertThrows(JsonException.class, () -> JacksonNodes.setInArray(arrayNode, 0, TextNode.valueOf("y")));
        assertThrows(JsonException.class, () -> JacksonNodes.addInArray(arrayNode, TextNode.valueOf("y")));
        assertThrows(JsonException.class, () -> JacksonNodes.addInArray(arrayNode, 0, TextNode.valueOf("y")));
        assertThrows(JsonException.class, () -> JacksonNodes.removeInObject(objectNode, "name"));
        assertThrows(JsonException.class, () -> JacksonNodes.removeInArray(arrayNode, 0));
    }
}
