package org.sjf4j.facade.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.NodeKind;
import org.sjf4j.node.Nodes;
import org.sjf4j.path.JsonPath;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.ValidationResult;

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

class GsonNodesTest {

    private static JsonElement read(String json) {
        return JsonParser.parseString(json);
    }

    @Test
    void basicOperations() {
        JsonElement root = read("{\"s\":\"v\",\"n\":2,\"b\":true,\"arr\":[1,2,2],\"obj\":{\"k\":\"v\"},\"nil\":null}");

        assertTrue(GsonNodes.isNode(root));
        assertEquals(NodeKind.OBJECT_FACADE, GsonNodes.kindOf(root));
        assertEquals(NodeKind.VALUE_NULL, GsonNodes.kindOf(root.getAsJsonObject().get("nil")));

        assertEquals("v", GsonNodes.toString(root.getAsJsonObject().get("s")));
        assertEquals("2", GsonNodes.asString(root.getAsJsonObject().get("n")));
        assertEquals(2, GsonNodes.toNumber(root.getAsJsonObject().get("n")).intValue());
        assertEquals(2, GsonNodes.asNumber(root.getAsJsonObject().get("n")).intValue());
        assertEquals(true, GsonNodes.toBoolean(root.getAsJsonObject().get("b")));
        assertEquals(true, GsonNodes.asBoolean(root.getAsJsonObject().get("b")));

        assertEquals("v", GsonNodes.toJsonObject(root.getAsJsonObject().get("obj")).getString("k"));
        assertEquals(6, GsonNodes.toMap(root).size());
        assertEquals(3, GsonNodes.toJsonArray(root.getAsJsonObject().get("arr")).size());
        assertEquals(3, GsonNodes.toList(root.getAsJsonObject().get("arr")).size());
        assertEquals(3, GsonNodes.toArray(root.getAsJsonObject().get("arr")).length);
        assertEquals(2, GsonNodes.toSet(root.getAsJsonObject().get("arr")).size());

        assertEquals(6, GsonNodes.sizeInObject(root));
        assertEquals(3, GsonNodes.sizeInArray(root.getAsJsonObject().get("arr")));

        Set<String> keys = GsonNodes.keySetInObject(root);
        assertTrue(keys.contains("obj"));
        assertTrue(keys.contains("arr"));

        assertEquals(6, GsonNodes.entrySetInObject(root).size());

        Iterator<Object> it = GsonNodes.iteratorInArray(root.getAsJsonObject().get("arr"));
        assertTrue(it.hasNext());
        assertEquals(1, Nodes.toNumber(it.next()).intValue());

        assertTrue(GsonNodes.containsInObject(root, "obj"));
        assertFalse(GsonNodes.containsInObject(root, "missing"));
        assertInstanceOf(JsonElement.class, GsonNodes.getInObject(root, "obj"));
        assertEquals(2, Nodes.toNumber(GsonNodes.getInArray(root.getAsJsonObject().get("arr"), 1)).intValue());

        Nodes.Access out = new Nodes.Access();
        out.reset();
        GsonNodes.accessInObject(root, Object.class, "obj", out);
        assertInstanceOf(JsonElement.class, out.node);
        assertEquals(JsonElement.class, out.type);
        assertFalse(out.insertable);

        out.reset();
        GsonNodes.accessInArray(root.getAsJsonObject().get("arr"), Object.class, 1, out);
        assertEquals(2, Nodes.toNumber(out.node).intValue());
        assertEquals(JsonElement.class, out.type);
        assertFalse(out.insertable);

        assertTrue(GsonNodes.anyMatchInArray(root.getAsJsonObject().get("arr"), (idx, value) -> Nodes.toNumber(value).intValue() == 2));
        assertTrue(GsonNodes.allMatchInArray(root.getAsJsonObject().get("arr"), (idx, value) -> Nodes.toNumber(value).intValue() >= 1));
    }

    @Test
    void jsonPathOperations() {
        JsonElement root = read("{\"a\":[1,2,3],\"b\":{\"x\":\"v\"},\"c\":true,\"d\":null}");

        assertEquals("v", JsonPath.compile("$.b.x").getString(root));
        assertEquals(2, JsonPath.compile("$.a[1]").getInteger(root));
        assertTrue(JsonPath.compile("$.c").getBoolean(root));
        assertEquals(NodeKind.VALUE_NULL, NodeKind.of(JsonPath.compile("$.d").getNode(root)));

        List<Object> numbers = JsonPath.compile("$.a[*]").find(root);
        assertEquals(3, numbers.size());
        assertEquals(3, Nodes.toNumber(numbers.get(2)).intValue());
    }

    @Test
    void jsonSchemaOperations() {
        JsonSchema schema = JsonSchema.fromJson("{\"type\":\"object\",\"required\":[\"id\",\"payload\"],\"properties\":{\"id\":{\"type\":\"integer\"},\"payload\":{\"type\":\"object\",\"required\":[\"flag\"],\"properties\":{\"flag\":{\"type\":\"boolean\"}}}}}");
        schema.compile();

        JsonElement ok = read("{\"id\":1,\"payload\":{\"flag\":true}}");
        JsonElement bad = read("{\"id\":1,\"payload\":{\"flag\":\"yes\"}}");

        ValidationResult okResult = schema.validate(ok);
        assertTrue(okResult.isValid(), okResult.getErrors().toString());
        assertFalse(schema.isValid(bad));
    }

    @Test
    void mixedWithNativeObjects() {
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
    void nodesApiOperations() {
        JsonElement root = read("{\"s\":\"v\",\"n\":2,\"b\":true,\"arr\":[1,2],\"obj\":{\"k\":\"v\"}}");
        Object arr = GsonNodes.getInObject(root, "arr");

        assertEquals("v", Nodes.toString(GsonNodes.getInObject(root, "s")));
        assertEquals("2", Nodes.asString(GsonNodes.getInObject(root, "n")));
        assertEquals(2, Nodes.toNumber(GsonNodes.getInObject(root, "n")).intValue());
        assertTrue(Nodes.toBoolean(GsonNodes.getInObject(root, "b")));

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
    void strictAndUnsupportedCases() {
        JsonElement root = read("{\"s\":\"v\",\"n\":2,\"b\":true,\"arr\":[1,2],\"obj\":{\"x\":1}}");

        assertThrows(JsonException.class, () -> GsonNodes.toString(root.getAsJsonObject().get("n")));
        assertThrows(JsonException.class, () -> GsonNodes.toNumber(root.getAsJsonObject().get("s")));
        assertThrows(JsonException.class, () -> GsonNodes.toBoolean(root.getAsJsonObject().get("n")));

        assertThrows(JsonException.class, () -> JsonPath.compile("$.obj.y").ensurePut(root, 5));
        assertThrows(JsonException.class, () -> JsonPath.compile("$.arr[1]").replace(root, 9));
        assertThrows(JsonException.class, () -> JsonPath.compile("$.arr[0]").remove(root));
    }
}
