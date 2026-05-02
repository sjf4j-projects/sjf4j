package org.sjf4j.facade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacadeNodesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testPresenceAndKindDispatch() {
        assertTrue(FacadeNodes.isJackson2NodesPresent());
        assertTrue(FacadeNodes.isGsonNodesPresent());

        ObjectNode jacksonObject = MAPPER.createObjectNode();
        jacksonObject.put("name", "han");
        ArrayNode jacksonArray = MAPPER.createArrayNode().add(1).add("x");

        JsonObject gsonObject = JsonParser.parseString("{\"name\":\"han\"}").getAsJsonObject();
        JsonArray gsonArray = JsonParser.parseString("[1,\"x\"]").getAsJsonArray();

        assertTrue(FacadeNodes.isNode(jacksonObject));
        assertTrue(FacadeNodes.isNode(gsonObject));
        assertTrue(FacadeNodes.isNode(jacksonObject.getClass()));
        assertTrue(FacadeNodes.isNode(gsonObject.getClass()));
        assertFalse(FacadeNodes.isNode("x"));
        assertFalse(FacadeNodes.isNode(String.class));

        assertEquals(NodeKind.OBJECT_FACADE, FacadeNodes.kindOf(jacksonObject));
        assertEquals(NodeKind.ARRAY_FACADE, FacadeNodes.kindOf(jacksonArray));
        assertEquals(NodeKind.VALUE_STRING_FACADE, FacadeNodes.kindOf(TextNode.valueOf("x")));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, FacadeNodes.kindOf(BooleanNode.TRUE));
        assertEquals(NodeKind.VALUE_NULL, FacadeNodes.kindOf(JsonNodeFactory.instance.nullNode()));

        assertEquals(NodeKind.OBJECT_FACADE, FacadeNodes.kindOf(gsonObject));
        assertEquals(NodeKind.ARRAY_FACADE, FacadeNodes.kindOf(gsonArray));
        assertEquals(NodeKind.VALUE_STRING_FACADE, FacadeNodes.kindOf(new JsonPrimitive("x")));
        assertEquals(NodeKind.VALUE_BOOLEAN_FACADE, FacadeNodes.kindOf(new JsonPrimitive(true)));
        assertEquals(NodeKind.VALUE_NULL, FacadeNodes.kindOf(JsonNull.INSTANCE));

        assertEquals(NodeKind.OBJECT_FACADE, FacadeNodes.kindOf(jacksonObject.getClass()));
        assertEquals(NodeKind.OBJECT_FACADE, FacadeNodes.kindOf(gsonObject.getClass()));
        assertThrows(JsonException.class, () -> FacadeNodes.kindOf("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.kindOf(String.class));
    }

    @Test
    void testJacksonDispatchOperations() {
        ObjectNode objectNode = MAPPER.createObjectNode();
        objectNode.put("name", "han");
        objectNode.put("age", 18);
        objectNode.put("active", true);
        ArrayNode arrayNode = MAPPER.createArrayNode().add("x").add(2).add(false);

        assertEquals("han", FacadeNodes.toString(objectNode.get("name")));
        assertEquals("18", FacadeNodes.asString(objectNode.get("age")));
        assertEquals(18, FacadeNodes.toNumber(objectNode.get("age")).intValue());
        assertEquals(18, FacadeNodes.asNumber(objectNode.get("age")).intValue());
        assertEquals(18, FacadeNodes.asNumber(TextNode.valueOf("18")).intValue());
        assertNull(FacadeNodes.asNumber(objectNode));
        assertTrue(FacadeNodes.toBoolean(objectNode.get("active")));
        assertTrue(FacadeNodes.asBoolean(objectNode.get("active")));
        assertTrue(FacadeNodes.asBoolean(TextNode.valueOf("true")));
        assertTrue(FacadeNodes.asBoolean(MAPPER.getNodeFactory().numberNode(1)));
        assertNull(FacadeNodes.asBoolean(objectNode));

        assertEquals("han", FacadeNodes.toJsonObject(objectNode).getString("name"));
        assertEquals("han", FacadeNodes.toMap(objectNode).get("name").toString().replace("\"", ""));
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
        assertTrue(FacadeNodes.replaceInObject(objectNode, (key, value) -> key.equals("name") ? TextNode.valueOf("jack") : value));
        assertFalse(FacadeNodes.replaceInObject(objectNode, (key, value) -> value));
        assertTrue(FacadeNodes.removeIfInObject(objectNode, (key, value) -> key.equals("active") || key.equals("missing")));
        assertFalse(objectNode.has("active"));
        assertFalse(FacadeNodes.removeIfInObject(objectNode, (key, value) -> false));
        assertEquals("jack", objectNode.get("name").textValue());

        List<Integer> visitedArray = new ArrayList<>();
        FacadeNodes.forEachArray(arrayNode, (idx, value) -> visitedArray.add(idx));
        assertEquals(Arrays.asList(0, 1, 2), visitedArray);
        assertTrue(FacadeNodes.anyMatchArray(arrayNode, (idx, value) -> idx == 1));
        assertFalse(FacadeNodes.anyMatchArray(arrayNode, (idx, value) -> idx == 9));

        assertNull(FacadeNodes.putInObject(objectNode, "x", MAPPER.valueToTree("y")));
        assertEquals("y", objectNode.get("x").textValue());
        assertEquals(2, FacadeNodes.toNumber(FacadeNodes.setInArray(arrayNode, 1, MAPPER.valueToTree(9))).intValue());
        FacadeNodes.addInArray(arrayNode, MAPPER.valueToTree(true));
        FacadeNodes.addInArray(arrayNode, -1, MAPPER.valueToTree("mid"));
        assertEquals("mid", arrayNode.get(3).textValue());
        assertEquals("jack", FacadeNodes.asString(FacadeNodes.removeInObject(objectNode, "name")));
        assertEquals(true, FacadeNodes.toBoolean(FacadeNodes.removeInArray(arrayNode, -1)));
    }

    @Test
    void testGsonDispatchOperationsAndUnknownFallbacks() {
        JsonObject objectNode = JsonParser.parseString("{\"name\":\"han\",\"age\":18,\"active\":true}").getAsJsonObject();
        JsonArray arrayNode = JsonParser.parseString("[\"x\",2,false]").getAsJsonArray();

        assertEquals("han", FacadeNodes.toString(objectNode.get("name")));
        assertEquals("18", FacadeNodes.asString(objectNode.get("age")));
        assertEquals(18, FacadeNodes.toNumber(objectNode.get("age")).intValue());
        assertEquals(18, FacadeNodes.asNumber(objectNode.get("age")).intValue());
        assertEquals(18, FacadeNodes.asNumber(new JsonPrimitive("18")).intValue());
        assertNull(FacadeNodes.asNumber(objectNode));
        assertTrue(FacadeNodes.toBoolean(objectNode.get("active")));
        assertTrue(FacadeNodes.asBoolean(objectNode.get("active")));
        assertTrue(FacadeNodes.asBoolean(new JsonPrimitive("true")));
        assertTrue(FacadeNodes.asBoolean(new JsonPrimitive(1)));
        assertNull(FacadeNodes.asBoolean(objectNode));

        assertEquals("han", FacadeNodes.toJsonObject(objectNode).getString("name"));
        Map<String, Object> map = FacadeNodes.toMap(objectNode);
        assertEquals("han", ((JsonPrimitive) map.get("name")).getAsString());
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
        assertFalse(access.puttable);
        FacadeNodes.accessInObject(objectNode, null, "missing", access);
        assertNull(access.node);
        assertFalse(access.puttable);
        FacadeNodes.accessInArray(arrayNode, null, 0, access);
        assertNotNull(access.node);
        assertFalse(access.puttable);
        FacadeNodes.accessInArray(arrayNode, null, null, access);
        assertNull(access.node);
        assertFalse(access.puttable);
        FacadeNodes.accessInArray(arrayNode, null, 3, access);
        assertNull(access.node);
        assertFalse(access.puttable);
        FacadeNodes.accessInArray(arrayNode, null, 4, access);
        assertNull(access.node);
        assertFalse(access.puttable);

        List<String> visitedObject = new ArrayList<>();
        FacadeNodes.forEachObject(objectNode, (key, value) -> visitedObject.add(key));
        assertEquals(3, visitedObject.size());
        assertTrue(FacadeNodes.anyMatchObject(objectNode, (key, value) -> key.equals("age")));
        assertFalse(FacadeNodes.anyMatchObject(objectNode, (key, value) -> key.equals("missing")));
        assertTrue(FacadeNodes.replaceInObject(objectNode, (key, value) -> key.equals("name") ? new JsonPrimitive("jack") : value));
        assertFalse(FacadeNodes.replaceInObject(objectNode, (key, value) -> value));
        assertEquals("jack", objectNode.get("name").getAsString());
        assertTrue(FacadeNodes.removeIfInObject(objectNode, (key, value) -> key.equals("active") || key.equals("missing")));
        assertFalse(objectNode.has("active"));
        assertFalse(FacadeNodes.removeIfInObject(objectNode, (key, value) -> false));

        List<Integer> visitedArray = new ArrayList<>();
        FacadeNodes.forEachArray(arrayNode, (idx, value) -> visitedArray.add(idx));
        assertEquals(Arrays.asList(0, 1, 2), visitedArray);
        assertTrue(FacadeNodes.anyMatchArray(arrayNode, (idx, value) -> idx == 1));
        assertFalse(FacadeNodes.anyMatchArray(arrayNode, (idx, value) -> idx == 9));

        assertThrows(JsonException.class, () -> FacadeNodes.putInObject(objectNode, "x", new JsonPrimitive("y")));
        assertThrows(JsonException.class, () -> FacadeNodes.setInArray(arrayNode, 0, new JsonPrimitive("y")));
        assertThrows(JsonException.class, () -> FacadeNodes.addInArray(arrayNode, new JsonPrimitive("y")));
        assertThrows(JsonException.class, () -> FacadeNodes.addInArray(arrayNode, 0, new JsonPrimitive("y")));
        assertThrows(JsonException.class, () -> FacadeNodes.removeInObject(objectNode, "name"));
        assertThrows(JsonException.class, () -> FacadeNodes.removeInArray(arrayNode, 0));

        assertThrows(JsonException.class, () -> FacadeNodes.toString("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.asString("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.toNumber("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.asNumber("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.toBoolean("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.asBoolean("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.toJsonObject("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.toMap("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.toJsonArray("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.toList("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.toArray("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.toSet("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.forEachObject("x", (k, v) -> {}));
        assertThrows(JsonException.class, () -> FacadeNodes.anyMatchObject("x", (k, v) -> true));
        assertThrows(JsonException.class, () -> FacadeNodes.replaceInObject("x", (k, v) -> v));
        assertThrows(JsonException.class, () -> FacadeNodes.removeIfInObject("x", (k, v) -> true));
        assertThrows(JsonException.class, () -> FacadeNodes.forEachArray("x", (i, v) -> {}));
        assertThrows(JsonException.class, () -> FacadeNodes.anyMatchArray("x", (i, v) -> true));
        assertThrows(JsonException.class, () -> FacadeNodes.sizeInObject("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.sizeInArray("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.keySetInObject("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.entrySetInObject("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.iteratorInArray("x"));
        assertThrows(JsonException.class, () -> FacadeNodes.containsInObject("x", "k"));
        assertThrows(JsonException.class, () -> FacadeNodes.getInObject("x", "k"));
        assertThrows(JsonException.class, () -> FacadeNodes.getInArray("x", 0));
        assertThrows(JsonException.class, () -> FacadeNodes.accessInObject("x", null, "k", new Nodes.Access()));
        assertThrows(JsonException.class, () -> FacadeNodes.accessInArray("x", null, 0, new Nodes.Access()));
        assertThrows(JsonException.class, () -> FacadeNodes.putInObject("x", "k", "v"));
        assertThrows(JsonException.class, () -> FacadeNodes.setInArray("x", 0, "v"));
        assertThrows(JsonException.class, () -> FacadeNodes.addInArray("x", "v"));
        assertThrows(JsonException.class, () -> FacadeNodes.addInArray("x", 0, "v"));
        assertThrows(JsonException.class, () -> FacadeNodes.removeInObject("x", "k"));
        assertThrows(JsonException.class, () -> FacadeNodes.removeInArray("x", 0));
    }
}
