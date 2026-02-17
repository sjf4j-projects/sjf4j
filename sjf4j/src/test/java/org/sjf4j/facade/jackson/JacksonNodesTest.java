package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPath;
import org.sjf4j.schema.JsonSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonNodesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode read(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void basicOperations() throws Exception {
        JsonNode root = read("{\"s\":\"v\",\"n\":2,\"b\":true,\"arr\":[1,2,2],\"obj\":{\"k\":\"v\"},\"nil\":null}");

        assertTrue(JacksonNodes.isNode(root));
        assertEquals(NodeKind.OBJECT_FACADE, JacksonNodes.kindOf(root));
        assertEquals(NodeKind.VALUE_NULL, JacksonNodes.kindOf(root.get("nil")));

        assertEquals("v", JacksonNodes.toString(root.get("s")));
        assertEquals("2", JacksonNodes.asString(root.get("n")));
        assertEquals(2, JacksonNodes.toNumber(root.get("n")).intValue());
        assertEquals(2, JacksonNodes.asNumber(root.get("n")).intValue());
        assertEquals(true, JacksonNodes.toBoolean(root.get("b")));
        assertEquals(true, JacksonNodes.asBoolean(root.get("b")));

        assertEquals("v", JacksonNodes.toJsonObject(root.get("obj")).getString("k"));
        assertEquals(6, JacksonNodes.toMap(root).size());
        assertEquals(3, JacksonNodes.toJsonArray(root.get("arr")).size());
        assertEquals(3, JacksonNodes.toList(root.get("arr")).size());
        assertEquals(3, JacksonNodes.toArray(root.get("arr")).length);
        assertEquals(2, JacksonNodes.toSet(root.get("arr")).size());

        assertEquals(6, JacksonNodes.sizeInObject(root));
        assertEquals(3, JacksonNodes.sizeInArray(root.get("arr")));

        Set<String> keys = JacksonNodes.keySetInObject(root);
        assertTrue(keys.contains("obj"));
        assertTrue(keys.contains("arr"));

        assertEquals(6, JacksonNodes.entrySetInObject(root).size());

        Iterator<Object> it = JacksonNodes.iteratorInArray(root.get("arr"));
        assertTrue(it.hasNext());
        assertEquals(1, Nodes.toNumber(it.next()).intValue());

        assertTrue(JacksonNodes.containsInObject(root, "obj"));
        assertFalse(JacksonNodes.containsInObject(root, "missing"));
        assertInstanceOf(JsonNode.class, JacksonNodes.getInObject(root, "obj"));
        assertEquals(2, Nodes.toNumber(JacksonNodes.getInArray(root.get("arr"), 1)).intValue());

        Nodes.Access out = new Nodes.Access();
        out.reset();
        JacksonNodes.accessInObject(root, Object.class, "obj", out);
        assertInstanceOf(JsonNode.class, out.node);
        assertEquals(JsonNode.class, out.type);
        assertFalse(out.insertable);

        out.reset();
        JacksonNodes.accessInArray(root.get("arr"), Object.class, 1, out);
        assertEquals(2, Nodes.toNumber(out.node).intValue());
        assertEquals(JsonNode.class, out.type);
        assertFalse(out.insertable);

        assertTrue(JacksonNodes.anyMatchInArray(root.get("arr"), (idx, value) -> Nodes.toNumber(value).intValue() == 2));
        assertTrue(JacksonNodes.allMatchInArray(root.get("arr"), (idx, value) -> Nodes.toNumber(value).intValue() >= 1));
    }

    @Test
    void jsonPathOperations() throws Exception {
        JsonNode root = read("{\"a\":[1,2,3],\"b\":{\"x\":\"v\"},\"c\":true,\"d\":null}");

        assertEquals("v", JsonPath.compile("$.b.x").getString(root));
        assertEquals(2, JsonPath.compile("$.a[1]").getInteger(root));
        assertTrue(JsonPath.compile("$.c").getBoolean(root));
        assertEquals(NodeKind.VALUE_NULL, NodeKind.of(JsonPath.compile("$.d").getNode(root)));

        List<Object> numbers = JsonPath.compile("$.a[*]").find(root);
        assertEquals(3, numbers.size());
        assertEquals(3, Nodes.toNumber(numbers.get(2)).intValue());
    }

    @Test
    void jsonSchemaOperations() throws Exception {
        JsonSchema schema = JsonSchema.fromJson("{\"type\":\"object\",\"required\":[\"id\",\"payload\"],\"properties\":{\"id\":{\"type\":\"integer\"},\"payload\":{\"type\":\"object\",\"required\":[\"flag\"],\"properties\":{\"flag\":{\"type\":\"boolean\"}}}}}");
        schema.compile();

        JsonNode ok = read("{\"id\":1,\"payload\":{\"flag\":true}}");
        JsonNode bad = read("{\"id\":1,\"payload\":{\"flag\":\"yes\"}}");

        assertTrue(schema.isValid(ok));
        assertFalse(schema.isValid(bad));
    }

    @Test
    void mixedWithNativeObjects() throws Exception {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("nativeNum", 7);
        root.put("doc", read("{\"k\":\"v\",\"arr\":[10,20]}"));
        root.put("items", new ArrayList<Object>(Arrays.asList("x", read("{\"enabled\":true}"))));

        assertEquals(7, JsonPath.compile("$.nativeNum").getInteger(root));
        assertEquals("v", JsonPath.compile("$.doc.k").getString(root));
        assertEquals(20, JsonPath.compile("$.doc.arr[1]").getInteger(root));
        assertTrue(JsonPath.compile("$.items[1].enabled").getBoolean(root));

        JsonSchema schema = JsonSchema.fromJson("{\"type\":\"object\",\"required\":[\"nativeNum\",\"doc\"],\"properties\":{\"nativeNum\":{\"type\":\"integer\"},\"doc\":{\"type\":\"object\",\"required\":[\"k\"],\"properties\":{\"k\":{\"type\":\"string\"}}}}}");
        schema.compile();
        assertTrue(schema.isValid(root));
    }

    @Test
    void nodesApiOperations() throws Exception {
        JsonNode root = read("{\"s\":\"v\",\"n\":2,\"b\":true,\"arr\":[1,2],\"obj\":{\"k\":\"v\"}}");
        Object arr = JacksonNodes.getInObject(root, "arr");

        assertEquals("v", Nodes.toString(JacksonNodes.getInObject(root, "s")));
        assertEquals("2", Nodes.asString(JacksonNodes.getInObject(root, "n")));
        assertEquals(2, Nodes.toNumber(JacksonNodes.getInObject(root, "n")).intValue());
        assertTrue(Nodes.toBoolean(JacksonNodes.getInObject(root, "b")));

        assertEquals(5, Nodes.sizeInObject(root));
        assertEquals(2, Nodes.sizeInArray(arr));
        assertTrue(Nodes.containsInObject(root, "obj"));

        final int[] objectCount = new int[]{0};
        Nodes.visitObject(root, (k, v) -> objectCount[0]++);
        assertEquals(5, objectCount[0]);

        final int[] sum = new int[]{0};
        Nodes.visitArray(arr, (i, v) -> sum[0] += Nodes.toNumber(v).intValue());
        assertEquals(3, sum[0]);
        assertTrue(Nodes.anyMatchInArray(arr, (i, v) -> Nodes.toNumber(v).intValue() == 2));
        assertTrue(Nodes.allMatchInArray(arr, (i, v) -> Nodes.toNumber(v).intValue() >= 1));

        Nodes.Access out = new Nodes.Access();
        Nodes.accessInObject(root, Object.class, "obj", out);
        assertNotNull(out.node);
        assertFalse(out.insertable);
        Nodes.accessInArray(arr, Object.class, 1, out);
        assertEquals(2, Nodes.toNumber(out.node).intValue());
        assertFalse(out.insertable);

        assertThrows(JsonException.class, () -> Nodes.putInObject(root, "x", 1));
        assertThrows(JsonException.class, () -> Nodes.setInArray(arr, 0, 9));
        assertThrows(JsonException.class, () -> Nodes.addInArray(arr, 3));
        assertThrows(JsonException.class, () -> Nodes.removeInArray(arr, 0));
    }

    @Test
    void strictAndUnsupportedCases() throws Exception {
        JsonNode root = read("{\"s\":\"v\",\"n\":2,\"b\":true,\"arr\":[1,2],\"obj\":{\"x\":1}}");

        assertThrows(JsonException.class, () -> JacksonNodes.kindOf("not-node"));
        assertThrows(JsonException.class, () -> JacksonNodes.toString(root.get("n")));
        assertThrows(JsonException.class, () -> JacksonNodes.toNumber(root.get("s")));
        assertThrows(JsonException.class, () -> JacksonNodes.toBoolean(root.get("n")));

        assertThrows(JsonException.class, () -> JsonPath.compile("$.obj.y").ensurePut(root, 5));
        assertThrows(JsonException.class, () -> JsonPath.compile("$.arr[1]").replace(root, 9));
        assertThrows(JsonException.class, () -> JsonPath.compile("$.arr[0]").remove(root));
    }
}
