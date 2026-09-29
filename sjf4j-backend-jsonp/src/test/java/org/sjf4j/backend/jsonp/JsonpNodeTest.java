package org.sjf4j.backend.jsonp;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.backend.jsonp.external.JsonpNodeProvider;
import org.sjf4j.exception.NodeException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalNodeRegistry;

import java.io.File;
import java.io.StringReader;
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

class JsonpNodeTest {
    @Test
    void providerIsDiscoveredAndClassifiesJsonpNodes() {
        ExternalNode<JsonValue> node = node();

        assertSame(JsonValue.class, node.nodeType());
        assertEquals(JsonType.OBJECT, node.jsonTypeOfClass(JsonObject.class));
        assertEquals(JsonType.ARRAY, node.jsonTypeOfClass(JsonArray.class));
        assertEquals(JsonType.STRING, node.jsonTypeOfClass(JsonString.class));
        assertEquals(JsonType.NUMBER, node.jsonTypeOfClass(JsonNumber.class));
        assertEquals(JsonType.UNKNOWN, node.jsonTypeOfClass(JsonValue.class));
        assertEquals(JsonType.OBJECT, node.jsonType(value("{}")));
        assertEquals(JsonType.ARRAY, node.jsonType(value("[]")));
        assertEquals(JsonType.STRING, node.jsonType(value("\"text\"")));
        assertEquals(JsonType.NUMBER, node.jsonType(value("12")));
        assertEquals(JsonType.BOOLEAN, node.jsonType(JsonValue.TRUE));
        assertEquals(JsonType.NULL, node.jsonType(JsonValue.NULL));
    }

    @Test
    void rawOfClassifiesJsonpNodes() {
        JsonObject object = value("{}").asJsonObject();
        JsonArray array = value("[]").asJsonArray();
        JsonString string = (JsonString) value("\"text\"");
        JsonNumber number = (JsonNumber) value("12");

        assertEquals(JsonType.OBJECT, JsonType.rawOf(object.getClass()));
        assertEquals(JsonType.ARRAY, JsonType.rawOf(array.getClass()));
        assertEquals(JsonType.STRING, JsonType.rawOf(string.getClass()));
        assertEquals(JsonType.NUMBER, JsonType.rawOf(number.getClass()));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void providerIsDiscoverableWithoutJsonp() throws Exception {
        URL coreClasses = ExternalNode.class.getProtectionDomain().getCodeSource().getLocation();
        URL integrationClasses = JsonpNodeProvider.class.getProtectionDomain().getCodeSource().getLocation();
        URL integrationResources = resourceRoot(JsonpNodeProvider.class.getResource(
                "/META-INF/services/org.sjf4j.external.ExternalNodeProvider"));

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { coreClasses, integrationClasses, integrationResources }, null)) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("jakarta.json.JsonValue", false, loader));
            Class providerType = Class.forName("org.sjf4j.external.ExternalNodeProvider", true, loader);
            ServiceLoader providers = ServiceLoader.load(providerType, loader);
            Object provider = providers.iterator().next();

            assertEquals("org.sjf4j.backend.jsonp.external.JsonpNodeProvider", provider.getClass().getName());
            assertNull(providerType.getMethod("externalNode").invoke(provider));
        }
    }

    @Test
    void convertsScalarsStrictlyAndLeniently() {
        ExternalNode<JsonValue> node = node();

        assertEquals("text", node.toString(value("\"text\"")));
        assertEquals("12", node.asString(value("12")));
        assertEquals("false", node.asString(JsonValue.FALSE));
        assertEquals(12, node.toNumber(value("12")).intValue());
        assertTrue(node.toBoolean(JsonValue.TRUE));
        assertEquals(12, node.asNumber(value("\"12\"")).intValue());
        assertEquals(1, node.asNumber(JsonValue.TRUE).intValue());
        assertEquals(0, node.asNumber(JsonValue.FALSE).intValue());
        assertTrue(node.asBoolean(value("\"true\"")));
        assertTrue(node.asBoolean(value("1")));
        assertNull(node.toString(JsonValue.NULL));
        assertNull(node.toNumber(JsonValue.NULL));
        assertNull(node.toBoolean(JsonValue.NULL));
        assertNull(node.asBoolean(JsonValue.NULL));

        assertThrows(NodeException.class, () -> node.toString(value("12")));
        assertThrows(NodeException.class, () -> node.toNumber(JsonValue.TRUE));
        assertThrows(NodeException.class, () -> node.toBoolean(value("\"true\"")));
        assertThrows(NodeException.class, () -> node.asString(value("{}")));
        assertThrows(NodeException.class, () -> node.asString(value("[]")));
        assertThrows(NodeException.class, () -> node.asNumber(value("{}")));
        assertThrows(NodeException.class, () -> node.asNumber(value("[]")));
        assertThrows(NodeException.class, () -> node.asBoolean(value("{}")));
        assertThrows(NodeException.class, () -> node.asBoolean(value("[]")));
        assertThrows(NodeException.class, () -> node.jsonType(null));
    }

    @Test
    void traversesJsonpObjectsAndArraysReadOnly() {
        ExternalNode<JsonValue> node = node();
        JsonObject object = value("{\"name\":\"value\",\"nil\":null,\"items\":[true,2]}").asJsonObject();
        JsonArray array = object.getJsonArray("items");

        ArrayList<String> properties = new ArrayList<>();
        node.forEachObject(object, (key, item) -> properties.add(key + ':' + item));
        assertEquals(Arrays.asList("name:\"value\"", "nil:null", "items:[true,2]"), properties);
        assertTrue(node.anyMatchObject(object, (key, item) -> key.equals("name") &&
                ((JsonString) item).getString().equals("value")));
        assertEquals(3, node.sizeInObject(object));
        assertTrue(node.containsInObject(object, "nil"));
        assertSame(JsonValue.NULL, node.getInObject(object, "nil"));

        Set<String> keys = node.keySetInObject(object);
        Set<Map.Entry<String, Object>> entries = node.entrySetInObject(object);
        assertTrue(keys.contains("name"));
        assertEquals(3, entries.size());
        assertEquals("name", entries.iterator().next().getKey());
        assertThrows(UnsupportedOperationException.class,
                () -> entries.iterator().next().setValue(JsonValue.TRUE));
        Iterator<String> keyIterator = keys.iterator();
        keyIterator.next();
        assertThrows(UnsupportedOperationException.class, keyIterator::remove);

        ArrayList<String> elements = new ArrayList<>();
        node.forEachArray(array, (index, item) -> elements.add(index + ":" + item));
        assertEquals(Arrays.asList("0:true", "1:2"), elements);
        assertTrue(node.anyMatchInArray(array, (index, item) -> index == 1 &&
                ((jakarta.json.JsonNumber) item).intValue() == 2));
        assertEquals(2, node.sizeInArray(array));
        Iterator<Object> iterator = node.iteratorInArray(array);
        assertSame(JsonValue.TRUE, iterator.next());
        assertEquals(2, ((jakarta.json.JsonNumber) iterator.next()).intValue());
        assertFalse(iterator.hasNext());
    }

    @Test
    void readsAccessMetadataAndNegativeArrayIndexes() {
        ExternalNode<JsonValue> node = node();
        JsonObject object = value("{\"nil\":null,\"items\":[true,2]}").asJsonObject();
        JsonArray array = object.getJsonArray("items");
        Nodes.Access access = new Nodes.Access();

        node.getAccessInObject(object, "nil", access);
        assertTrue(access.present);
        assertSame(JsonValue.NULL, access.node);
        node.getAccessInObject(object, "missing", access);
        assertFalse(access.present);
        assertNull(access.node);

        assertSame(array.get(1), node.getInArray(array, -1));
        assertSame(array.get(0), node.getInArray(array, -2));
        assertNull(node.getInArray(array, -3));
        assertNull(node.getInArray(array, 2));
        node.getAccessInArray(array, -1, access);
        assertTrue(access.present);
        assertSame(array.get(1), access.node);
        node.getAccessInArray(array, 2, access);
        assertFalse(access.present);
        assertNull(access.node);
    }

    @Test
    void shallowCopiesContainersAndSharesValues() {
        ExternalNode<JsonValue> node = node();
        JsonObject object = value("{\"child\":{}}").asJsonObject();
        JsonArray array = value("[[]]").asJsonArray();

        JsonObject objectCopy = (JsonObject) node.copy(object);
        JsonArray arrayCopy = (JsonArray) node.copy(array);
        assertFalse(objectCopy == object);
        assertSame(object.get("child"), objectCopy.get("child"));
        assertFalse(arrayCopy == array);
        assertSame(array.get(0), arrayCopy.get(0));
        assertSame(JsonValue.NULL, node.copy(JsonValue.NULL));
        JsonValue scalar = value("\"value\"");
        assertSame(scalar, node.copy(scalar));
    }

    @Test
    void rejectsAllWritesAndFactories() {
        ExternalNode<JsonValue> node = node();
        JsonObject object = value("{}").asJsonObject();
        JsonArray array = value("[true]").asJsonArray();
        Nodes.Access access = new Nodes.Access();

        assertUnsupported(() -> node.replaceAllInObject(object, (key, item) -> item));
        assertUnsupported(() -> node.removeIfInObject(object, (key, item) -> true));
        assertUnsupported(() -> node.putAccessInObject(object, "key", access));
        assertUnsupported(() -> node.putAccessInArray(array, 0, access));
        assertUnsupported(() -> node.putInObject(object, "key", JsonValue.TRUE));
        assertUnsupported(() -> node.setInArray(array, 0, JsonValue.FALSE));
        assertUnsupported(() -> node.addInArray(array, JsonValue.FALSE));
        assertUnsupported(() -> node.addInArray(array, 0, JsonValue.FALSE));
        assertUnsupported(() -> node.removeInObject(object, "key"));
        assertUnsupported(() -> node.removeInArray(array, 0));
        assertUnsupported(() -> node.createObjectNode(JsonObject.class));
        assertUnsupported(() -> node.createArrayNode(JsonArray.class));
    }

    @SuppressWarnings("unchecked")
    private static ExternalNode<JsonValue> node() {
        ExternalNode<?> discovered = ExternalNodeRegistry.resolve(JsonObject.class);
        assertNotNull(discovered);
        return (ExternalNode<JsonValue>) discovered;
    }

    private static JsonValue value(String json) {
        try (jakarta.json.JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readValue();
        }
    }

    private static void assertUnsupported(Runnable operation) {
        assertThrows(NodeException.class, operation::run);
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
