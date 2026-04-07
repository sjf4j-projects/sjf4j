package org.sjf4j.facade.jackson2;

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

class Jackson2NodesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testKindsAndScalarConversions() {
        ObjectNode objectNode = MAPPER.createObjectNode().put("name", "han");
        ArrayNode arrayNode = MAPPER.createArrayNode().add(1).add("x");

        assertTrue(Jackson2Nodes.isNode(objectNode));
        assertTrue(Jackson2Nodes.isNode(objectNode.getClass()));
        assertFalse(Jackson2Nodes.isNode("x"));
        assertFalse(Jackson2Nodes.isNode(String.class));

        assertEquals(NodeKind.VALUE_NULL, Jackson2Nodes.kindOf(JsonNodeFactory.instance.nullNode()));
        assertEquals(NodeKind.VALUE_STRING_FACADE, Jackson2Nodes.kindOf(TextNode.valueOf("x")));
        assertEquals(NodeKind.VALUE_NUMBER_FACADE, Jackson2Nodes.kindOf(JsonNodeFactory.instance.numberNode(1)));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, Jackson2Nodes.kindOf(BooleanNode.TRUE));
        assertEquals(NodeKind.OBJECT_FACADE, Jackson2Nodes.kindOf(objectNode));
        assertEquals(NodeKind.ARRAY_FACADE, Jackson2Nodes.kindOf(arrayNode));
        assertEquals(NodeKind.OBJECT_FACADE, Jackson2Nodes.kindOf(ObjectNode.class));
        assertEquals(NodeKind.ARRAY_FACADE, Jackson2Nodes.kindOf(ArrayNode.class));
        assertEquals(NodeKind.VALUE_STRING_FACADE, Jackson2Nodes.kindOf(TextNode.class));
        assertEquals(NodeKind.VALUE_NUMBER_FACADE, Jackson2Nodes.kindOf(JsonNodeFactory.instance.numberNode(1).getClass()));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, Jackson2Nodes.kindOf(BooleanNode.class));
        assertEquals(NodeKind.UNKNOWN, Jackson2Nodes.kindOf(com.fasterxml.jackson.databind.JsonNode.class));
        assertEquals(NodeKind.UNKNOWN, Jackson2Nodes.kindOf(new BinaryNode(new byte[]{1})));
        assertThrows(JsonException.class, () -> Jackson2Nodes.kindOf(new POJONode("x")));
        assertThrows(JsonException.class, () -> Jackson2Nodes.kindOf("x"));
        assertThrows(JsonException.class, () -> Jackson2Nodes.kindOf(String.class));

        assertEquals("x", Jackson2Nodes.toString(TextNode.valueOf("x")));
        assertEquals("1", Jackson2Nodes.asString(JsonNodeFactory.instance.numberNode(1)));
        assertEquals(1, Jackson2Nodes.toNumber(JsonNodeFactory.instance.numberNode(1)).intValue());
        assertEquals(2, Jackson2Nodes.asNumber(TextNode.valueOf("2")).intValue());
        assertNull(Jackson2Nodes.asNumber(objectNode));
        assertTrue(Jackson2Nodes.toBoolean(BooleanNode.TRUE));
        assertTrue(Jackson2Nodes.asBoolean(TextNode.valueOf("true")));
        assertTrue(Jackson2Nodes.asBoolean(JsonNodeFactory.instance.numberNode(1)));
        assertNull(Jackson2Nodes.asBoolean(objectNode));

        assertThrows(JsonException.class, () -> Jackson2Nodes.toString(objectNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.toNumber(objectNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.toBoolean(objectNode));
    }

    @Test
    void testContainersVisitorsAndUnsupportedMutations() {
        ObjectNode objectNode = MAPPER.createObjectNode();
        objectNode.put("name", "han");
        objectNode.put("age", 18);
        ArrayNode arrayNode = MAPPER.createArrayNode().add("x").add(2).add(false);

        assertEquals("han", Jackson2Nodes.toJsonObject(objectNode).getString("name"));
        assertEquals(2, Jackson2Nodes.toMap(objectNode).size());
        assertEquals("x", Jackson2Nodes.toJsonArray(arrayNode).getString(0));
        assertEquals(3, Jackson2Nodes.toList(arrayNode).size());
        assertEquals(3, Jackson2Nodes.toArray(arrayNode).length);
        assertEquals(3, Jackson2Nodes.toSet(arrayNode).size());
        assertEquals(2, Jackson2Nodes.sizeInObject(objectNode));
        assertEquals(3, Jackson2Nodes.sizeInArray(arrayNode));
        assertTrue(Jackson2Nodes.keySetInObject(objectNode).contains("name"));
        assertTrue(Jackson2Nodes.entrySetInObject(objectNode).stream().anyMatch(e -> e.getKey().equals("age")));
        assertTrue(Jackson2Nodes.containsInObject(objectNode, "name"));
        assertEquals(18, Jackson2Nodes.toNumber(Jackson2Nodes.getInObject(objectNode, "age")).intValue());
        assertEquals(2, Jackson2Nodes.toNumber(Jackson2Nodes.getInArray(arrayNode, 1)).intValue());

        Nodes.Access access = new Nodes.Access();
        Jackson2Nodes.accessInObject(objectNode, null, "name", access);
        assertNotNull(access.node);
        Jackson2Nodes.accessInArray(arrayNode, null, 0, access);
        assertNotNull(access.node);

        List<String> keys = new ArrayList<>();
        Jackson2Nodes.visitObject(objectNode, (key, value) -> keys.add(key));
        assertEquals(2, keys.size());
        assertTrue(Jackson2Nodes.anyMatchInObject(objectNode, (key, value) -> key.equals("age")));
        assertFalse(Jackson2Nodes.anyMatchInObject(objectNode, (key, value) -> false));
        assertTrue(Jackson2Nodes.transformInObject(objectNode, (key, value) -> key.equals("name") ? TextNode.valueOf("jack") : value));
        assertFalse(Jackson2Nodes.transformInObject(objectNode, (key, value) -> value));

        List<Integer> indexes = new ArrayList<>();
        Jackson2Nodes.visitArray(arrayNode, (idx, value) -> indexes.add(idx));
        assertEquals(List.of(0, 1, 2), indexes);
        assertTrue(Jackson2Nodes.anyMatchInArray(arrayNode, (idx, value) -> idx == 1));
        assertFalse(Jackson2Nodes.anyMatchInArray(arrayNode, (idx, value) -> false));
        assertTrue(Jackson2Nodes.allMatchInArray(arrayNode, (idx, value) -> idx < 3));
        assertFalse(Jackson2Nodes.allMatchInArray(arrayNode, (idx, value) -> idx < 2));

        assertThrows(JsonException.class, () -> Jackson2Nodes.toJsonObject(arrayNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.toMap(arrayNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.toJsonArray(objectNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.toList(objectNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.toArray(objectNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.toSet(objectNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.sizeInObject(arrayNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.sizeInArray(objectNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.keySetInObject(arrayNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.entrySetInObject(arrayNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.iteratorInArray(objectNode));
        assertThrows(JsonException.class, () -> Jackson2Nodes.containsInObject(arrayNode, "name"));
        assertThrows(JsonException.class, () -> Jackson2Nodes.getInObject(arrayNode, "name"));
        assertThrows(JsonException.class, () -> Jackson2Nodes.getInArray(objectNode, 0));
        assertThrows(JsonException.class, () -> Jackson2Nodes.accessInObject(arrayNode, null, "name", new Nodes.Access()));
        assertThrows(JsonException.class, () -> Jackson2Nodes.accessInArray(objectNode, null, 0, new Nodes.Access()));
        assertThrows(JsonException.class, () -> Jackson2Nodes.visitObject(arrayNode, (k, v) -> {}));
        assertThrows(JsonException.class, () -> Jackson2Nodes.anyMatchInObject(arrayNode, (k, v) -> true));
        assertThrows(JsonException.class, () -> Jackson2Nodes.transformInObject(arrayNode, (k, v) -> v));
        assertThrows(JsonException.class, () -> Jackson2Nodes.visitArray(objectNode, (i, v) -> {}));
        assertThrows(JsonException.class, () -> Jackson2Nodes.anyMatchInArray(objectNode, (i, v) -> true));
        assertThrows(JsonException.class, () -> Jackson2Nodes.allMatchInArray(objectNode, (i, v) -> true));
        assertThrows(JsonException.class, () -> Jackson2Nodes.putInObject(objectNode, "x", TextNode.valueOf("y")));
        assertThrows(JsonException.class, () -> Jackson2Nodes.setInArray(arrayNode, 0, TextNode.valueOf("y")));
        assertThrows(JsonException.class, () -> Jackson2Nodes.addInArray(arrayNode, TextNode.valueOf("y")));
        assertThrows(JsonException.class, () -> Jackson2Nodes.addInArray(arrayNode, 0, TextNode.valueOf("y")));
        assertThrows(JsonException.class, () -> Jackson2Nodes.removeInObject(objectNode, "name"));
        assertThrows(JsonException.class, () -> Jackson2Nodes.removeInArray(arrayNode, 0));
    }
}
