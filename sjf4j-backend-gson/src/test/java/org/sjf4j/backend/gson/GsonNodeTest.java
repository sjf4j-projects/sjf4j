package org.sjf4j.backend.gson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.exception.JsonException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalNodeRegistry;
import org.sjf4j.backend.gson.external.GsonNodeProvider;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ServiceLoader;

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
    @SuppressWarnings({"rawtypes", "unchecked"})
    void providerIsDiscoverableWithoutGson() throws Exception {
        URL coreClasses = ExternalNode.class.getProtectionDomain().getCodeSource().getLocation();
        URL integrationClasses = GsonNodeProvider.class.getProtectionDomain().getCodeSource().getLocation();
        URL integrationResources = resourceRoot(GsonNodeProvider.class.getResource(
                "/META-INF/services/org.sjf4j.external.ExternalNodeProvider"));

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { coreClasses, integrationClasses, integrationResources }, null)) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("com.google.gson.JsonElement", false, loader));
            Class providerType = Class.forName("org.sjf4j.external.ExternalNodeProvider", true, loader);
            ServiceLoader providers = ServiceLoader.load(providerType, loader);
            Object provider = providers.iterator().next();

            assertEquals("org.sjf4j.backend.gson.external.GsonNodeProvider", provider.getClass().getName());
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
        assertTrue(node.asBoolean(JsonParser.parseString("\"true\"")));
        assertTrue(node.asBoolean(JsonParser.parseString("1")));
        assertNull(node.asNumber(JsonParser.parseString("true")));
        assertNull(node.asBoolean(JsonParser.parseString("null")));

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
        assertTrue(node.replaceInObject(object, (key, value) -> key.equals("name") ? JsonParser.parseString("\"updated\"") : value));
        assertEquals("updated", object.get("name").getAsString());
        assertTrue(node.removeIfInObject(object, (key, value) -> key.equals("drop")));
        assertFalse(object.has("drop"));

        ArrayList<String> elements = new ArrayList<>();
        node.forEachArray(array, (index, value) -> elements.add(index + ":" + value));
        assertEquals(Arrays.asList("0:true", "1:2"), elements);
        assertTrue(node.anyMatchArray(array, (index, value) -> index == 1 && ((JsonElement) value).getAsInt() == 2));
    }

    @Test
    void accessPreservesGsonReadOnlyAndArrayBoundsSemantics() {
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
        assertFalse(access.puttable);
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
        assertFalse(access.puttable);
        assertNull(access.node);
    }

    @Test
    void rejectsAllUnsupportedMutationsWithLegacyMessages() {
        ExternalNode<JsonElement> node = node();
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        JsonElement value = JsonParser.parseString("true");

        assertUnsupported(() -> node.putInObject(object, "value", value), "putInObject");
        assertUnsupported(() -> node.setInArray(array, 0, value), "setInArray");
        assertUnsupported(() -> node.addInArray(array, value), "addInArray");
        assertUnsupported(() -> node.addInArray(array, 0, value), "addInArray");
        assertUnsupported(() -> node.removeInObject(object, "value"), "removeInObject");
        assertUnsupported(() -> node.removeInArray(array, 0), "removeInArray");
    }

    @SuppressWarnings("unchecked")
    private static ExternalNode<JsonElement> node() {
        ExternalNode<?> discovered = ExternalNodeRegistry.resolve(JsonObject.class);
        assertNotNull(discovered);
        return (ExternalNode<JsonElement>) discovered;
    }

    private static void assertStrictRejection(Runnable operation, String expected) {
        JsonException exception = assertThrows(JsonException.class, operation::run);
        assertEquals("expected " + expected + ", but was com.google.gson.JsonPrimitive", exception.getMessage());
    }

    private static void assertUnsupported(Runnable operation, String method) {
        JsonException exception = assertThrows(JsonException.class, operation::run);
        assertEquals("unsupported external node operation '" + method + "'", exception.getMessage());
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
