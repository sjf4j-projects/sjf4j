package org.sjf4j.backend.gson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.exception.NodeException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalRegistry;
import org.sjf4j.backend.gson.external.GsonExternalProvider;
import org.sjf4j.path.JsonPath;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GsonNodeTest {
    @Test
    void providerIsDiscoveredAndClassifiesGsonNodes() {
        ExternalNode<JsonElement> node = node();

        assertSame(JsonElement.class, node.nodeType());
        assertEquals(JsonType.OBJECT, node.jsonTypeOfClass(JsonObject.class));
        assertEquals(JsonType.ARRAY, node.jsonTypeOfClass(JsonArray.class));
        assertEquals(JsonType.OBJECT, node.jsonType(JsonParser.parseString("{}")));
    }

    @Test
    void nodesDispatchesToGsonNodes() {
        JsonObject object = new JsonObject();
        object.add("name", new JsonPrimitive("value"));
        JsonPrimitive written = new JsonPrimitive("written");

        assertTrue(Nodes.keySetInObject(object).contains("name"));
        assertNull(Nodes.putInObject(object, "written", written));
        assertSame(written, object.get("written"));
        assertSame(JsonObject.class, object.getClass());

        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive("first"));
        JsonPrimitive last = new JsonPrimitive("last");
        array.add(last);
        JsonPrimitive appended = new JsonPrimitive("appended");

        assertSame(last, Nodes.getInArray(array, -1));
        assertNull(Nodes.putInArray(array, array.size(), appended));
        assertSame(appended, array.get(2));
        assertSame(JsonArray.class, array.getClass());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void providerIsDiscoverableWithoutGson() throws Exception {
        URL coreClasses = ExternalNode.class.getProtectionDomain().getCodeSource().getLocation();
        URL integrationClasses = GsonExternalProvider.class.getProtectionDomain().getCodeSource().getLocation();
        URL integrationResources = resourceRoot(GsonExternalProvider.class.getResource(
                "/META-INF/services/org.sjf4j.external.ExternalProvider"));

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { coreClasses, integrationClasses, integrationResources }, null)) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("com.google.gson.JsonElement", false, loader));
            Class providerType = Class.forName("org.sjf4j.external.ExternalProvider", true, loader);
            ServiceLoader providers = ServiceLoader.load(providerType, loader);
            Object provider = providers.iterator().next();

            assertEquals("org.sjf4j.backend.gson.external.GsonExternalProvider", provider.getClass().getName());
            assertNull(providerType.getMethod("externalNode").invoke(provider));
        }
    }

    @Test
    void convertsScalarsWithLegacyStrictAndLenientSemantics() {
        ExternalNode<JsonElement> node = node();

        assertEquals("text", node.toString(JsonParser.parseString("\"text\"")));
        assertEquals("12", node.asString(JsonParser.parseString("12")));
        assertEquals(12, node.toNumber(JsonParser.parseString("12")).intValue());
        assertTrue(node.toBoolean(JsonParser.parseString("true")));
        assertEquals(12, node.asNumber(JsonParser.parseString("\"12\"")));
        assertEquals(1, node.asNumber(JsonParser.parseString("true")));
        assertEquals(0, node.asNumber(JsonParser.parseString("false")));
        assertTrue(node.asBoolean(JsonParser.parseString("\"true\"")));
        assertTrue(node.asBoolean(JsonParser.parseString("1")));
        assertNull(node.asBoolean(JsonParser.parseString("null")));
        assertThrows(NodeException.class, () -> node.asNumber(JsonParser.parseString("{}")));
        assertThrows(NodeException.class, () -> node.asNumber(JsonParser.parseString("[]")));
        assertThrows(NodeException.class, () -> node.asBoolean(JsonParser.parseString("{}")));
        assertThrows(NodeException.class, () -> node.asBoolean(JsonParser.parseString("[]")));
        assertNull(node.toString(JsonNull.INSTANCE));
        assertNull(node.toNumber(JsonNull.INSTANCE));
        assertNull(node.toBoolean(JsonNull.INSTANCE));
        assertNull(node.asString(JsonNull.INSTANCE));
        assertNull(node.asNumber(JsonNull.INSTANCE));

        assertStrictRejection(() -> node.toString(JsonParser.parseString("12")), "JsonPrimitive(String)");
        assertStrictRejection(() -> node.toNumber(JsonParser.parseString("true")), "JsonPrimitive(Number)");
        assertStrictRejection(() -> node.toBoolean(JsonParser.parseString("\"true\"")), "JsonPrimitive(Boolean)");
        assertThrows(IllegalStateException.class, () -> node.asString(JsonParser.parseString("[]")));
    }

    @Test
    void traversesObjectsAndArrays() {
        ExternalNode<JsonElement> node = node();
        JsonObject object = JsonParser.parseString("{\"name\":\"value\",\"drop\":false,\"items\":[true,2]}").getAsJsonObject();
        JsonArray array = object.getAsJsonArray("items");

        ArrayList<String> properties = new ArrayList<>();
        node.forEachObject(object, (key, value) -> properties.add(key + ':' + value));
        assertEquals(Arrays.asList("name:\"value\"", "drop:false", "items:[true,2]"), properties);
        assertTrue(node.anyMatchObject(object, (key, value) -> key.equals("name") &&
                ((JsonElement) value).getAsString().equals("value")));
        assertTrue(node.replaceAllInObject(object, (key, value) -> key.equals("name") ? JsonParser.parseString("\"updated\"") : value));
        assertEquals("updated", object.get("name").getAsString());
        assertTrue(node.removeIfInObject(object, (key, value) -> key.equals("drop")));
        assertFalse(object.has("drop"));

        ArrayList<String> elements = new ArrayList<>();
        node.forEachArray(array, (index, value) -> elements.add(index + ":" + value));
        assertEquals(Arrays.asList("0:true", "1:2"), elements);
        assertTrue(node.anyMatchInArray(array, (index, value) -> index == 1 && ((JsonElement) value).getAsInt() == 2));
    }

    @Test
    void exposesLiveObjectKeyAndEntryViews() {
        ExternalNode<JsonElement> node = node();
        JsonObject object = JsonParser.parseString("{\"name\":\"value\"}").getAsJsonObject();

        Set<String> keys = node.keySetInObject(object);
        Set<Map.Entry<String, Object>> entries = node.entrySetInObject(object);
        assertTrue(keys.contains("name"));
        Map.Entry<String, Object> entry = entries.iterator().next();
        assertEquals("value", ((JsonElement) entry.setValue(null)).getAsString());
        assertSame(JsonNull.INSTANCE, object.get("name"));
        assertInvalidValue(() -> entry.setValue("invalid"));
        assertSame(JsonNull.INSTANCE, object.get("name"));

        object.addProperty("added", true);
        assertTrue(keys.contains("added"));
        assertEquals(2, entries.size());
        object.remove("added");
        assertEquals(1, entries.size());
        object.addProperty("added", true);
        Iterator<Map.Entry<String, Object>> iterator = entries.iterator();
        String iteratorRemoved = iterator.next().getKey();
        iterator.remove();
        assertFalse(object.has(iteratorRemoved));
        Map.Entry<String, Object> setRemoved = entries.iterator().next();
        String setRemovedKey = setRemoved.getKey();
        assertTrue(entries.remove(setRemoved));
        assertFalse(object.has(setRemovedKey));
    }

    @Test
    void getsArrayElementsWithNegativeIndexesAndNullForInvalidIndexes() {
        ExternalNode<JsonElement> node = node();
        JsonArray array = JsonParser.parseString("[true,2]").getAsJsonArray();

        assertSame(array.get(1), node.getInArray(array, -1));
        assertSame(array.get(0), node.getInArray(array, -2));
        assertNull(node.getInArray(array, -3));
        assertNull(node.getInArray(array, 2));
    }

    @Test
    void copiesOnlyOuterGsonContainers() {
        JsonObject object = new JsonObject();
        JsonObject childObject = new JsonObject();
        object.add("child", childObject);
        JsonArray array = new JsonArray();
        JsonArray childArray = new JsonArray();
        array.add(childArray);

        JsonObject objectCopy = Nodes.copy(object);
        JsonArray arrayCopy = Nodes.copy(array);
        assertFalse(objectCopy == object);
        assertSame(childObject, objectCopy.get("child"));
        assertFalse(arrayCopy == array);
        assertSame(childArray, arrayCopy.get(0));
        assertSame(JsonNull.INSTANCE, Nodes.copy(JsonNull.INSTANCE));
        JsonPrimitive primitive = new JsonPrimitive("value");
        assertSame(primitive, Nodes.copy(primitive));
    }

    @Test
    void createsNativeGsonContainers() {
        assertTrue(Nodes.createObjectNode(JsonObject.class) instanceof JsonObject);
        assertTrue(Nodes.createArrayNode(JsonArray.class) instanceof JsonArray);
        assertTrue(Nodes.createObjectNode(JsonElement.class) instanceof JsonObject);
        assertTrue(Nodes.createArrayNode(JsonElement.class) instanceof JsonArray);
        assertThrows(NodeException.class, () -> Nodes.createObjectNode(JsonArray.class));
        assertThrows(NodeException.class, () -> Nodes.createArrayNode(JsonObject.class));
    }

    @Test
    void ensuresGsonContainersThroughJsonElementAccessType() {
        JsonObject object = new JsonObject();

        JsonPath.parse("$.a.b.c").ensurePut(object, new JsonPrimitive("value"));
        JsonPath.parse("$.arr[+].value").ensurePut(object, new JsonPrimitive("item"));

        assertTrue(object.get("a").isJsonObject());
        assertTrue(object.getAsJsonObject("a").get("b").isJsonObject());
        assertEquals("value", object.getAsJsonObject("a").getAsJsonObject("b").get("c").getAsString());
        assertTrue(object.get("arr").isJsonArray());
        assertTrue(object.getAsJsonArray("arr").get(0).isJsonObject());
        assertEquals("item", object.getAsJsonArray("arr").get(0).getAsJsonObject().get("value").getAsString());
    }

    @Test
    void ensurePutReplacesGsonJsonNullIntermediateContainers() {
        JsonObject object = JsonParser.parseString("{\"a\":null}").getAsJsonObject();
        JsonArray array = JsonParser.parseString("[null]").getAsJsonArray();

        JsonPath.parse("$.a.b").ensurePut(object, new JsonPrimitive("object"));
        JsonPath.parse("$[0].b").ensurePut(array, new JsonPrimitive("array"));

        assertEquals("object", object.getAsJsonObject("a").get("b").getAsString());
        assertEquals("array", array.get(0).getAsJsonObject().get("b").getAsString());
    }

    @Test
    void ensurePutIfAbsentReplacesGsonJsonNull() {
        JsonObject object = new JsonObject();
        object.add("value", JsonNull.INSTANCE);
        object.add("pointer", JsonNull.INSTANCE);
        JsonArray array = new JsonArray();
        array.add(JsonNull.INSTANCE);
        object.add("array", array);

        assertNull(JsonPath.parse("$.value").ensurePutIfAbsent(object, new JsonPrimitive("name")));
        assertNull(JsonPath.parse("/pointer").ensurePutIfAbsent(object, new JsonPrimitive("key")));
        assertNull(JsonPath.parse("$.array[0]").ensurePutIfAbsent(object, new JsonPrimitive("index")));
        assertEquals("name", object.get("value").getAsString());
        assertEquals("key", object.get("pointer").getAsString());
        assertEquals("index", array.get(0).getAsString());
    }

    @Test
    void accessExposesGsonWriteAndArrayBoundsSemantics() {
        ExternalNode<JsonElement> node = node();
        JsonObject object = JsonParser.parseString("{\"name\":\"value\",\"nil\":null,\"items\":[true,2]}").getAsJsonObject();
        JsonArray array = object.getAsJsonArray("items");
        Nodes.Access access = new Nodes.Access();

        access.type = String.class;
        access.puttable = true;
        node.getAccessInObject(object, "name", access);
        assertTrue(access.present);
        assertEquals("value", ((JsonElement) access.node).getAsString());
        assertSame(String.class, access.type);
        assertTrue(access.puttable);
        node.getAccessInObject(object, "nil", access);
        assertTrue(access.present);
        assertTrue(((JsonElement) access.node).isJsonNull());
        node.getAccessInObject(object, "missing", access);
        assertFalse(access.present);
        assertNull(access.node);
        node.putAccessInObject(object, "name", access);
        assertTrue(access.puttable);
        assertSame(JsonElement.class, access.type);
        assertEquals("value", ((JsonElement) access.node).getAsString());

        access.type = String.class;
        access.puttable = true;
        node.getAccessInArray(array, -1, access);
        assertTrue(access.present);
        assertEquals(2, ((JsonElement) access.node).getAsInt());
        assertSame(String.class, access.type);
        assertTrue(access.puttable);
        node.getAccessInArray(array, 2, access);
        assertFalse(access.present);
        assertNull(access.node);
        node.getAccessInArray(array, -3, access);
        assertFalse(access.present);
        assertNull(access.node);
        access.present = true;
        node.putAccessInArray(array, 9, access);
        assertFalse(access.puttable);
        assertNull(access.node);
        assertSame(JsonElement.class, access.type);
        assertTrue(access.present);
        node.putAccessInArray(array, null, access);
        assertTrue(access.puttable);
        assertNull(access.node);
        node.putAccessInArray(array, 0, access);
        assertTrue(access.puttable);
        assertTrue(((JsonElement) access.node).getAsBoolean());
        node.putAccessInArray(array, 2, access);
        assertTrue(access.puttable);
        assertNull(access.node);
        node.putAccessInArray(array, -1, access);
        assertTrue(access.puttable);
        assertEquals(2, ((JsonElement) access.node).getAsInt());
    }

    @Test
    void mutatesGsonContainersWithNullCanonicalizationAndNegativeIndexes() {
        ExternalNode<JsonElement> node = node();
        JsonObject object = new JsonObject();
        JsonElement value = new JsonPrimitive("value");

        assertNull(node.putInObject(object, "value", null));
        assertSame(JsonNull.INSTANCE, object.get("value"));
        assertSame(JsonNull.INSTANCE, node.putInObject(object, "value", value));
        assertSame(value, node.removeInObject(object, "value"));
        assertNull(node.removeInObject(object, "missing"));

        JsonArray array = JsonParser.parseString("[\"zero\",\"one\"]").getAsJsonArray();
        assertEquals("one", ((JsonElement) node.setInArray(array, -1, null)).getAsString());
        assertSame(JsonNull.INSTANCE, array.get(1));
        node.addInArray(array, null);
        assertSame(JsonNull.INSTANCE, array.get(2));
        node.addInArray(array, -1, null);
        assertSame(JsonNull.INSTANCE, array.get(2));
        assertSame(JsonNull.INSTANCE, node.removeInArray(array, -1));
        assertEquals(3, array.size());
    }

    @Test
    void rejectsInvalidValuesAndArrayIndexes() {
        ExternalNode<JsonElement> node = node();
        JsonObject object = new JsonObject();
        JsonArray array = JsonParser.parseString("[true]").getAsJsonArray();

        assertInvalidValue(() -> node.putInObject(object, "value", "invalid"));
        assertInvalidValue(() -> node.setInArray(array, 0, "invalid"));
        assertInvalidValue(() -> node.addInArray(array, "invalid"));
        assertInvalidValue(() -> node.addInArray(array, 0, "invalid"));
        assertThrows(NodeException.class, () -> node.setInArray(array, -2, JsonNull.INSTANCE));
        assertThrows(NodeException.class, () -> node.setInArray(array, 1, JsonNull.INSTANCE));
        assertThrows(NodeException.class, () -> node.addInArray(array, -2, JsonNull.INSTANCE));
        assertThrows(NodeException.class, () -> node.addInArray(array, 2, JsonNull.INSTANCE));
        assertThrows(NodeException.class, () -> node.removeInArray(array, -2));
        assertThrows(NodeException.class, () -> node.removeInArray(array, 1));

        node.addInArray(array, -1, JsonNull.INSTANCE);
        node.addInArray(array, array.size(), JsonNull.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    private static ExternalNode<JsonElement> node() {
        ExternalNode<?> discovered = ExternalRegistry.resolve(JsonObject.class);
        assertNotNull(discovered);
        return (ExternalNode<JsonElement>) discovered;
    }

    private static void assertStrictRejection(Runnable operation, String expected) {
        NodeException exception = assertThrows(NodeException.class, operation::run);
        assertEquals("expected " + expected + ", but was com.google.gson.JsonPrimitive", exception.getMessage());
    }

    private static void assertInvalidValue(Runnable operation) {
        NodeException exception = assertThrows(NodeException.class, operation::run);
        assertEquals("expected JsonElement or null, but was java.lang.String", exception.getMessage());
    }

    private static URL resourceRoot(URL resource) throws Exception {
        assertNotNull(resource);
        File root = new File(resource.toURI());
        for (int i = 0; i < 3; i++) {
            root = root.getParentFile();
        }
        return root.toURI().toURL();
    }
}
