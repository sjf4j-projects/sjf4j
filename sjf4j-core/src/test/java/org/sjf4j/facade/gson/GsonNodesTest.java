package org.sjf4j.facade.gson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;
import org.sjf4j.NodeKind;
import org.sjf4j.Nodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GsonNodesTest {

    @Test
    void testKindsAndScalarConversions() {
        JsonObject objectNode = JsonParser.parseString("{\"name\":\"han\"}").getAsJsonObject();
        JsonArray arrayNode = JsonParser.parseString("[1,\"x\"]").getAsJsonArray();

        assertTrue(GsonNodes.isNode(objectNode));
        assertTrue(GsonNodes.isNode(objectNode.getClass()));
        assertFalse(GsonNodes.isNode("x"));
        assertFalse(GsonNodes.isNode(String.class));

        assertEquals(NodeKind.VALUE_STRING_EXTERNAL, GsonNodes.kindOf(new JsonPrimitive("x")));
        assertEquals(NodeKind.VALUE_NUMBER_EXTERNAL, GsonNodes.kindOf(new JsonPrimitive(1)));
        assertEquals(NodeKind.VALUE_BOOLEAN_EXTERNAL, GsonNodes.kindOf(new JsonPrimitive(true)));
        assertEquals(NodeKind.OBJECT_EXTERNAL, GsonNodes.kindOf(objectNode));
        assertEquals(NodeKind.ARRAY_EXTERNAL, GsonNodes.kindOf(arrayNode));
        assertEquals(NodeKind.VALUE_NULL, GsonNodes.kindOf(JsonNull.INSTANCE));
        assertThrows(NodeException.class, () -> GsonNodes.kindOf("x"));

        assertEquals(NodeKind.OBJECT_EXTERNAL, GsonNodes.kindOf(JsonObject.class));
        assertEquals(NodeKind.ARRAY_EXTERNAL, GsonNodes.kindOf(JsonArray.class));
        assertEquals(NodeKind.VALUE_NULL, GsonNodes.kindOf(JsonNull.class));
        assertEquals(NodeKind.UNKNOWN, GsonNodes.kindOf(String.class));

        assertEquals("x", GsonNodes.toString(new JsonPrimitive("x")));
        assertEquals("1", GsonNodes.asString(new JsonPrimitive(1)));
        assertEquals(1, GsonNodes.toNumber(new JsonPrimitive(1)).intValue());
        assertEquals(2, GsonNodes.asNumber(new JsonPrimitive("2")).intValue());
        assertNull(GsonNodes.asNumber(objectNode));
        assertTrue(GsonNodes.toBoolean(new JsonPrimitive(true)));
        assertTrue(GsonNodes.asBoolean(new JsonPrimitive("true")));
        assertTrue(GsonNodes.asBoolean(new JsonPrimitive(1)));
        assertNull(GsonNodes.asBoolean(objectNode));

        assertThrows(NodeException.class, () -> GsonNodes.toString(objectNode));
        assertThrows(NodeException.class, () -> GsonNodes.toNumber(objectNode));
        assertThrows(NodeException.class, () -> GsonNodes.toBoolean(objectNode));
    }

    @Test
    void testContainersVisitorsAndUnsupportedMutations() {
        JsonObject objectNode = JsonParser.parseString("{\"name\":\"han\",\"age\":18}").getAsJsonObject();
        JsonArray arrayNode = JsonParser.parseString("[\"x\",2,false]").getAsJsonArray();

        assertEquals("han", GsonNodes.toJsonObject(objectNode).getString("name"));
        assertEquals(2, GsonNodes.toMap(objectNode).size());
        assertEquals("x", GsonNodes.toJsonArray(arrayNode).getString(0));
        assertEquals(3, GsonNodes.toList(arrayNode).size());
        assertEquals(3, GsonNodes.toArray(arrayNode).length);
        assertEquals(3, GsonNodes.toSet(arrayNode).size());
        assertEquals(2, GsonNodes.sizeInObject(objectNode));
        assertEquals(3, GsonNodes.sizeInArray(arrayNode));
        assertTrue(GsonNodes.keySetInObject(objectNode).contains("name"));
        assertTrue(GsonNodes.entrySetInObject(objectNode).stream().anyMatch(e -> e.getKey().equals("age")));
        assertTrue(GsonNodes.containsInObject(objectNode, "name"));
        assertEquals(18, GsonNodes.toNumber(GsonNodes.getInObject(objectNode, "age")).intValue());
        assertEquals(2, GsonNodes.toNumber(GsonNodes.getInArray(arrayNode, 1)).intValue());

        Nodes.Access access = new Nodes.Access();
        GsonNodes.putAccessInObject(objectNode, null, "name", access);
        assertEquals("han", ((JsonPrimitive) access.node).getAsString());
        assertEquals(JsonElement.class, access.type);
        assertFalse(access.puttable);
        GsonNodes.putAccessInObject(objectNode, null, "missing", access);
        assertNull(access.node);
        assertEquals(JsonElement.class, access.type);
        assertFalse(access.puttable);
        GsonNodes.putAccessInArray(arrayNode, null, 0, access);
        assertEquals("x", ((JsonPrimitive) access.node).getAsString());
        assertEquals(JsonElement.class, access.type);
        assertFalse(access.puttable);
        GsonNodes.putAccessInArray(arrayNode, null, null, access);
        assertNull(access.node);
        assertEquals(JsonElement.class, access.type);
        assertFalse(access.puttable);
        GsonNodes.putAccessInArray(arrayNode, null, 3, access);
        assertNull(access.node);
        assertEquals(JsonElement.class, access.type);
        assertFalse(access.puttable);
        GsonNodes.putAccessInArray(arrayNode, null, 4, access);
        assertNull(access.node);
        assertEquals(JsonElement.class, access.type);
        assertFalse(access.puttable);

        List<String> keys = new ArrayList<>();
        GsonNodes.forEachObject(objectNode, (key, value) -> keys.add(key));
        assertEquals(2, keys.size());
        assertTrue(GsonNodes.anyMatchObject(objectNode, (key, value) -> key.equals("age")));
        assertFalse(GsonNodes.anyMatchObject(objectNode, (key, value) -> false));
        assertTrue(GsonNodes.replaceInObject(objectNode, (key, value) -> key.equals("name") ? new JsonPrimitive("jack") : value));
        assertFalse(GsonNodes.replaceInObject(objectNode, (key, value) -> value));
        assertTrue(GsonNodes.removeIfInObject(objectNode, (key, value) -> key.equals("age") || key.equals("missing")));
        assertFalse(objectNode.has("age"));
        assertFalse(GsonNodes.removeIfInObject(objectNode, (key, value) -> false));

        List<Integer> indexes = new ArrayList<>();
        GsonNodes.forEachArray(arrayNode, (idx, value) -> indexes.add(idx));
        assertEquals(Arrays.asList(0, 1, 2), indexes);
        assertTrue(GsonNodes.anyMatchArray(arrayNode, (idx, value) -> idx == 1));
        assertFalse(GsonNodes.anyMatchArray(arrayNode, (idx, value) -> false));

        assertThrows(NodeException.class, () -> GsonNodes.toJsonObject(arrayNode));
        assertThrows(NodeException.class, () -> GsonNodes.toMap(arrayNode));
        assertThrows(NodeException.class, () -> GsonNodes.toJsonArray(objectNode));
        assertThrows(NodeException.class, () -> GsonNodes.toList(objectNode));
        assertThrows(NodeException.class, () -> GsonNodes.toArray(objectNode));
        assertThrows(NodeException.class, () -> GsonNodes.toSet(objectNode));
        assertThrows(NodeException.class, () -> GsonNodes.sizeInObject(arrayNode));
        assertThrows(NodeException.class, () -> GsonNodes.sizeInArray(objectNode));
        assertThrows(NodeException.class, () -> GsonNodes.keySetInObject(arrayNode));
        assertThrows(NodeException.class, () -> GsonNodes.entrySetInObject(arrayNode));
        assertThrows(NodeException.class, () -> GsonNodes.iteratorInArray(objectNode));
        assertThrows(NodeException.class, () -> GsonNodes.containsInObject(arrayNode, "name"));
        assertThrows(NodeException.class, () -> GsonNodes.getInObject(arrayNode, "name"));
        assertThrows(NodeException.class, () -> GsonNodes.getInArray(objectNode, 0));
        assertThrows(NodeException.class, () -> GsonNodes.putAccessInObject(arrayNode, null, "name", new Nodes.Access()));
        assertThrows(NodeException.class, () -> GsonNodes.putAccessInArray(objectNode, null, 0, new Nodes.Access()));
        assertThrows(NodeException.class, () -> GsonNodes.forEachObject(arrayNode, (k, v) -> {}));
        assertThrows(NodeException.class, () -> GsonNodes.anyMatchObject(arrayNode, (k, v) -> true));
        assertThrows(NodeException.class, () -> GsonNodes.replaceInObject(arrayNode, (k, v) -> v));
        assertThrows(NodeException.class, () -> GsonNodes.removeIfInObject(arrayNode, (k, v) -> true));
        assertThrows(NodeException.class, () -> GsonNodes.forEachArray(objectNode, (i, v) -> {}));
        assertThrows(NodeException.class, () -> GsonNodes.anyMatchArray(objectNode, (i, v) -> true));
        assertThrows(NodeException.class, () -> GsonNodes.putInObject(objectNode, "x", new JsonPrimitive("y")));
        assertThrows(NodeException.class, () -> GsonNodes.setInArray(arrayNode, 0, new JsonPrimitive("y")));
        assertThrows(NodeException.class, () -> GsonNodes.addInArray(arrayNode, new JsonPrimitive("y")));
        assertThrows(NodeException.class, () -> GsonNodes.addInArray(arrayNode, 0, new JsonPrimitive("y")));
        assertThrows(NodeException.class, () -> GsonNodes.removeInObject(objectNode, "name"));
        assertThrows(NodeException.class, () -> GsonNodes.removeInArray(arrayNode, 0));
    }
}
