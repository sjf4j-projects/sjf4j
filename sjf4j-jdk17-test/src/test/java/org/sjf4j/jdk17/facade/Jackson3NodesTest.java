package org.sjf4j.jdk17.facade;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.FacadeNodes;
import org.sjf4j.facade.jackson3.Jackson3Nodes;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BinaryNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.POJONode;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson3NodesTest {

    private static final JsonMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    @Test
    void testKindsAndScalarConversions() {
        ObjectNode objectNode = MAPPER.createObjectNode().put("name", "han");
        ArrayNode arrayNode = MAPPER.createArrayNode().add(1).add("x");

        assertTrue(Jackson3Nodes.isNode(objectNode));
        assertTrue(Jackson3Nodes.isNode(objectNode.getClass()));
        assertFalse(Jackson3Nodes.isNode("x"));
        assertFalse(Jackson3Nodes.isNode(String.class));

        assertEquals(NodeKind.VALUE_NULL, Jackson3Nodes.kindOf(JsonNodeFactory.instance.nullNode()));
        assertEquals(NodeKind.VALUE_STRING_FACADE, Jackson3Nodes.kindOf(StringNode.valueOf("x")));
        assertEquals(NodeKind.VALUE_NUMBER_FACADE, Jackson3Nodes.kindOf(JsonNodeFactory.instance.numberNode(1)));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, Jackson3Nodes.kindOf(BooleanNode.TRUE));
        assertEquals(NodeKind.OBJECT_FACADE, Jackson3Nodes.kindOf(objectNode));
        assertEquals(NodeKind.ARRAY_FACADE, Jackson3Nodes.kindOf(arrayNode));
        assertEquals(NodeKind.OBJECT_FACADE, Jackson3Nodes.kindOf(ObjectNode.class));
        assertEquals(NodeKind.ARRAY_FACADE, Jackson3Nodes.kindOf(ArrayNode.class));
        assertEquals(NodeKind.VALUE_STRING_FACADE, Jackson3Nodes.kindOf(StringNode.class));
        assertEquals(NodeKind.VALUE_NUMBER_FACADE, Jackson3Nodes.kindOf(JsonNodeFactory.instance.numberNode(1).getClass()));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, Jackson3Nodes.kindOf(BooleanNode.class));
        assertEquals(NodeKind.UNKNOWN, Jackson3Nodes.kindOf(JsonNode.class));
        assertEquals(NodeKind.UNKNOWN, Jackson3Nodes.kindOf(new BinaryNode(new byte[]{1})));
        assertThrows(JsonException.class, () -> Jackson3Nodes.kindOf(new POJONode("x")));
        assertThrows(JsonException.class, () -> Jackson3Nodes.kindOf("x"));
        assertThrows(JsonException.class, () -> Jackson3Nodes.kindOf(String.class));

        assertEquals("x", Jackson3Nodes.toString(StringNode.valueOf("x")));
        assertEquals("1", Jackson3Nodes.asString(JsonNodeFactory.instance.numberNode(1)));
        assertEquals(1, Jackson3Nodes.toNumber(JsonNodeFactory.instance.numberNode(1)).intValue());
        assertEquals(2, Jackson3Nodes.asNumber(StringNode.valueOf("2")).intValue());
        assertNull(Jackson3Nodes.asNumber(objectNode));
        assertTrue(Jackson3Nodes.toBoolean(BooleanNode.TRUE));
        assertTrue(Jackson3Nodes.asBoolean(StringNode.valueOf("true")));
        assertTrue(Jackson3Nodes.asBoolean(JsonNodeFactory.instance.numberNode(1)));
        assertNull(Jackson3Nodes.asBoolean(objectNode));

        assertThrows(JsonException.class, () -> Jackson3Nodes.toString(objectNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.toNumber(objectNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.toBoolean(objectNode));
    }

    @Test
    void testContainersVisitorsAndUnsupportedMutations() {
        ObjectNode objectNode = MAPPER.createObjectNode();
        objectNode.put("name", "han");
        objectNode.put("age", 18);
        ArrayNode arrayNode = MAPPER.createArrayNode().add("x").add(2).add(false);

        assertEquals("han", Jackson3Nodes.toJsonObject(objectNode).getString("name"));
        assertEquals(2, Jackson3Nodes.toMap(objectNode).size());
        assertEquals("x", Jackson3Nodes.toJsonArray(arrayNode).getString(0));
        assertEquals(3, Jackson3Nodes.toList(arrayNode).size());
        assertEquals(3, Jackson3Nodes.toArray(arrayNode).length);
        assertEquals(3, Jackson3Nodes.toSet(arrayNode).size());
        assertEquals(2, Jackson3Nodes.sizeInObject(objectNode));
        assertEquals(3, Jackson3Nodes.sizeInArray(arrayNode));
        assertTrue(Jackson3Nodes.keySetInObject(objectNode).contains("name"));
        assertTrue(Jackson3Nodes.entrySetInObject(objectNode).stream().anyMatch(e -> e.getKey().equals("age")));
        assertTrue(Jackson3Nodes.containsInObject(objectNode, "name"));
        assertEquals(18, Jackson3Nodes.toNumber(Jackson3Nodes.getInObject(objectNode, "age")).intValue());
        assertEquals(2, Jackson3Nodes.toNumber(Jackson3Nodes.getInArray(arrayNode, 1)).intValue());

        Nodes.Access access = new Nodes.Access();
        Jackson3Nodes.accessInObject(objectNode, null, "name", access);
        assertEquals("han", access.node.toString().replace("\"", ""));
        assertEquals(JsonNode.class, access.type);
        assertTrue(access.puttable);
        Jackson3Nodes.accessInObject(objectNode, null, "missing", access);
        assertNull(access.node);
        assertEquals(JsonNode.class, access.type);
        assertTrue(access.puttable);
        Jackson3Nodes.accessInArray(arrayNode, null, 0, access);
        assertNotNull(access.node);
        assertEquals(JsonNode.class, access.type);
        assertTrue(access.puttable);
        Jackson3Nodes.accessInArray(arrayNode, null, null, access);
        assertNull(access.node);
        assertEquals(JsonNode.class, access.type);
        assertTrue(access.puttable);
        Jackson3Nodes.accessInArray(arrayNode, null, 3, access);
        assertNull(access.node);
        assertEquals(JsonNode.class, access.type);
        assertTrue(access.puttable);
        Jackson3Nodes.accessInArray(arrayNode, null, 4, access);
        assertNull(access.node);
        assertEquals(JsonNode.class, access.type);
        assertFalse(access.puttable);

        List<String> keys = new ArrayList<>();
        Jackson3Nodes.forEachObject(objectNode, (key, value) -> keys.add(key));
        assertEquals(2, keys.size());
        assertTrue(Jackson3Nodes.anyMatchObject(objectNode, (key, value) -> key.equals("age")));
        assertFalse(Jackson3Nodes.anyMatchObject(objectNode, (key, value) -> false));
        assertTrue(Jackson3Nodes.replaceInObject(objectNode, (key, value) -> key.equals("name") ? StringNode.valueOf("jack") : value));
        assertFalse(Jackson3Nodes.replaceInObject(objectNode, (key, value) -> value));

        List<Integer> indexes = new ArrayList<>();
        Jackson3Nodes.forEachArray(arrayNode, (idx, value) -> indexes.add(idx));
        assertEquals(Arrays.asList(0, 1, 2), indexes);
        assertTrue(Jackson3Nodes.anyMatchArray(arrayNode, (idx, value) -> idx == 1));
        assertFalse(Jackson3Nodes.anyMatchArray(arrayNode, (idx, value) -> false));

        assertThrows(JsonException.class, () -> Jackson3Nodes.toJsonObject(arrayNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.toMap(arrayNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.toJsonArray(objectNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.toList(objectNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.toArray(objectNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.toSet(objectNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.sizeInObject(arrayNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.sizeInArray(objectNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.keySetInObject(arrayNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.entrySetInObject(arrayNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.iteratorInArray(objectNode));
        assertThrows(JsonException.class, () -> Jackson3Nodes.containsInObject(arrayNode, "name"));
        assertThrows(JsonException.class, () -> Jackson3Nodes.getInObject(arrayNode, "name"));
        assertThrows(JsonException.class, () -> Jackson3Nodes.getInArray(objectNode, 0));
        assertThrows(JsonException.class, () -> Jackson3Nodes.accessInObject(arrayNode, null, "name", new Nodes.Access()));
        assertThrows(JsonException.class, () -> Jackson3Nodes.accessInArray(objectNode, null, 0, new Nodes.Access()));
        assertThrows(JsonException.class, () -> Jackson3Nodes.forEachObject(arrayNode, (k, v) -> {}));
        assertThrows(JsonException.class, () -> Jackson3Nodes.anyMatchObject(arrayNode, (k, v) -> true));
        assertThrows(JsonException.class, () -> Jackson3Nodes.replaceInObject(arrayNode, (k, v) -> v));
        assertThrows(JsonException.class, () -> Jackson3Nodes.forEachArray(objectNode, (i, v) -> {}));
        assertThrows(JsonException.class, () -> Jackson3Nodes.anyMatchArray(objectNode, (i, v) -> true));
        assertNull(Jackson3Nodes.putInObject(objectNode, "x", StringNode.valueOf("y")));
        assertEquals("y", objectNode.get("x").asString());
        assertEquals(2, Jackson3Nodes.toNumber(Jackson3Nodes.setInArray(arrayNode, 1, JsonNodeFactory.instance.numberNode(9))).intValue());
        assertEquals(9, arrayNode.get(1).intValue());
        Jackson3Nodes.addInArray(arrayNode, BooleanNode.TRUE);
        assertTrue(arrayNode.get(3).booleanValue());
        Jackson3Nodes.addInArray(arrayNode, -1, StringNode.valueOf("mid"));
        assertEquals("mid", arrayNode.get(3).asString());
        assertEquals("jack", Jackson3Nodes.asString(Jackson3Nodes.removeInObject(objectNode, "name")));
        assertFalse(objectNode.has("name"));
        assertEquals(true, Jackson3Nodes.toBoolean(Jackson3Nodes.removeInArray(arrayNode, -1)));
        assertEquals(4, arrayNode.size());
    }

    @Test
    void testFacadeNodesDispatchesJackson3Nodes() {
        ObjectNode objectNode = MAPPER.createObjectNode();
        objectNode.put("name", "han");
        objectNode.put("age", 18);
        objectNode.put("active", true);
        ArrayNode arrayNode = MAPPER.createArrayNode().add("x").add(2).add(false);

        assertTrue(FacadeNodes.isJackson3NodesPresent());
        assertTrue(FacadeNodes.isNode(objectNode));
        assertTrue(FacadeNodes.isNode(objectNode.getClass()));
        assertEquals(NodeKind.OBJECT_FACADE, FacadeNodes.kindOf(objectNode));
        assertEquals(NodeKind.OBJECT_FACADE, FacadeNodes.kindOf(objectNode.getClass()));

        assertEquals("han", FacadeNodes.toString(objectNode.get("name")));
        assertEquals("18", FacadeNodes.asString(objectNode.get("age")));
        assertEquals(18, FacadeNodes.toNumber(objectNode.get("age")).intValue());
        assertEquals(18, FacadeNodes.asNumber(StringNode.valueOf("18")).intValue());
        assertTrue(FacadeNodes.toBoolean(objectNode.get("active")));
        assertTrue(FacadeNodes.asBoolean(StringNode.valueOf("true")));

        assertEquals("han", FacadeNodes.toJsonObject(objectNode).getString("name"));
        assertEquals(3, FacadeNodes.toMap(objectNode).size());
        assertEquals("x", FacadeNodes.toJsonArray(arrayNode).getString(0));
        assertEquals(3, FacadeNodes.toList(arrayNode).size());
        assertArrayEquals(FacadeNodes.toList(arrayNode).toArray(), FacadeNodes.toArray(arrayNode));
        assertEquals(3, FacadeNodes.toSet(arrayNode).size());
        assertEquals(3, FacadeNodes.sizeInObject(objectNode));
        assertEquals(3, FacadeNodes.sizeInArray(arrayNode));
        assertTrue(FacadeNodes.keySetInObject(objectNode).contains("name"));
        assertTrue(FacadeNodes.entrySetInObject(objectNode).stream().anyMatch(e -> e.getKey().equals("age")));

        Iterator<Object> iterator = FacadeNodes.iteratorInArray(arrayNode);
        assertTrue(iterator.hasNext());
        assertEquals("x", FacadeNodes.asString(iterator.next()));
        assertTrue(FacadeNodes.containsInObject(objectNode, "name"));
        assertEquals(18, FacadeNodes.toNumber(FacadeNodes.getInObject(objectNode, "age")).intValue());
        assertEquals(2, FacadeNodes.toNumber(FacadeNodes.getInArray(arrayNode, 1)).intValue());

        Nodes.Access access = new Nodes.Access();
        FacadeNodes.accessInObject(objectNode, null, "name", access);
        assertNotNull(access.node);
        assertTrue(access.puttable);
        FacadeNodes.accessInObject(objectNode, null, "missing", access);
        assertNull(access.node);
        assertTrue(access.puttable);
        FacadeNodes.accessInArray(arrayNode, null, 0, access);
        assertNotNull(access.node);
        assertTrue(access.puttable);
        FacadeNodes.accessInArray(arrayNode, null, null, access);
        assertNull(access.node);
        assertTrue(access.puttable);
        FacadeNodes.accessInArray(arrayNode, null, 3, access);
        assertNull(access.node);
        assertTrue(access.puttable);
        FacadeNodes.accessInArray(arrayNode, null, 4, access);
        assertNull(access.node);
        assertFalse(access.puttable);

        List<String> visitedObject = new ArrayList<>();
        FacadeNodes.forEachObject(objectNode, (key, value) -> visitedObject.add(key));
        assertEquals(3, visitedObject.size());
        assertTrue(FacadeNodes.anyMatchObject(objectNode, (key, value) -> key.equals("age")));
        assertFalse(FacadeNodes.anyMatchObject(objectNode, (key, value) -> key.equals("missing")));
        assertTrue(FacadeNodes.replaceInObject(objectNode, (key, value) -> key.equals("name") ? StringNode.valueOf("jack") : value));
        assertFalse(FacadeNodes.replaceInObject(objectNode, (key, value) -> value));
        assertEquals("jack", objectNode.get("name").asString());

        List<Integer> visitedArray = new ArrayList<>();
        FacadeNodes.forEachArray(arrayNode, (idx, value) -> visitedArray.add(idx));
        assertEquals(Arrays.asList(0, 1, 2), visitedArray);
        assertTrue(FacadeNodes.anyMatchArray(arrayNode, (idx, value) -> idx == 1));
        assertFalse(FacadeNodes.anyMatchArray(arrayNode, (idx, value) -> idx == 9));

        assertNull(FacadeNodes.putInObject(objectNode, "x", StringNode.valueOf("y")));
        assertEquals("y", objectNode.get("x").asString());
        assertEquals(2, FacadeNodes.toNumber(FacadeNodes.setInArray(arrayNode, 1, JsonNodeFactory.instance.numberNode(9))).intValue());
        FacadeNodes.addInArray(arrayNode, BooleanNode.TRUE);
        FacadeNodes.addInArray(arrayNode, -1, StringNode.valueOf("mid"));
        assertEquals("mid", arrayNode.get(3).asString());
        assertEquals("jack", FacadeNodes.asString(FacadeNodes.removeInObject(objectNode, "name")));
        assertEquals(true, FacadeNodes.toBoolean(FacadeNodes.removeInArray(arrayNode, -1)));
    }
}
