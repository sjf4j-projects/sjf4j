package org.sjf4j.backend.jackson2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.backend.jackson2.external.Jackson2ExternalProvider;
import org.sjf4j.exception.NodeException;
import org.sjf4j.external.ExternalNode;
import org.sjf4j.external.ExternalRegistry;
import org.sjf4j.path.JsonPath;
import org.sjf4j.patch.Patches;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

class Jackson2NodeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void discoversProviderAndWorksWithoutJacksonAtRuntime() throws Exception {
        assertSame(JsonNode.class, node().nodeType());
        assertEquals(JsonType.OBJECT, node().jsonTypeOfClass(ObjectNode.class));

        URL core = ExternalNode.class.getProtectionDomain().getCodeSource().getLocation();
        URL classes = Jackson2ExternalProvider.class.getProtectionDomain().getCodeSource().getLocation();
        URL resources = resourceRoot(
                Jackson2ExternalProvider.class.getResource("/META-INF/services/org.sjf4j.external.ExternalProvider"));
        try (URLClassLoader loader = new URLClassLoader(new URL[]{core, classes, resources}, null)) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("com.fasterxml.jackson.databind.JsonNode", false, loader));
            Class<?> providerType = Class.forName("org.sjf4j.external.ExternalProvider", true, loader);
            Object provider = ServiceLoader.load(providerType, loader).iterator().next();
            assertNull(providerType.getMethod("externalNode").invoke(provider));
        }
    }

    @Test
    void nodesDispatchesToJackson2Nodes() {
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        object.set("name", JsonNodeFactory.instance.textNode("value"));
        JsonNode written = JsonNodeFactory.instance.textNode("written");

        assertTrue(Nodes.keySetInObject(object).contains("name"));
        assertNull(Nodes.putInObject(object, "written", written));
        assertSame(written, object.get("written"));
        assertSame(ObjectNode.class, object.getClass());

        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        array.add(JsonNodeFactory.instance.textNode("first"));
        JsonNode last = JsonNodeFactory.instance.textNode("last");
        array.add(last);
        JsonNode appended = JsonNodeFactory.instance.textNode("appended");

        assertSame(last, Nodes.getInArray(array, -1));
        assertNull(Nodes.putInArray(array, array.size(), appended));
        assertSame(appended, array.get(2));
        assertSame(ArrayNode.class, array.getClass());
    }

    @Test
    void convertsScalarsAndDistinguishesJsonNull() throws Exception {
        ExternalNode<JsonNode> node = node();
        assertEquals("text", node.toString(MAPPER.readTree("\"text\"")));
        assertEquals("12", node.asString(MAPPER.readTree("12")));
        assertEquals(12, node.toNumber(MAPPER.readTree("12")).intValue());
        assertTrue(node.toBoolean(MAPPER.readTree("true")));
        assertEquals(12, node.asNumber(MAPPER.readTree("\"12\"")).intValue());
        assertEquals(1, node.asNumber(MAPPER.readTree("true")).intValue());
        assertTrue(node.asBoolean(MAPPER.readTree("1")));
        assertNull(node.toString(NullNode.instance));
        assertNull(node.asNumber(NullNode.instance));
        assertNull(node.asBoolean(NullNode.instance));
        assertThrows(NodeException.class, () -> node.asNumber(object("{}")));
        assertThrows(NodeException.class, () -> node.asBoolean(array("[]")));
    }

    @Test
    void traversesAndExposesLiveObjectViews() throws Exception {
        ExternalNode<JsonNode> node = node();
        ObjectNode object = object("{\"name\":\"value\",\"drop\":false,\"items\":[true,2]}");
        assertTrue(node.anyMatchObject(object, (key, value) -> key.equals("name")));
        assertTrue(node.anyMatchInArray((ArrayNode) object.get("items"), (i, value) -> i == 1));
        assertTrue(node.replaceAllInObject(object, (key, value) ->
                key.equals("name") ? JsonNodeFactory.instance.textNode("updated") : value));
        assertTrue(node.removeIfInObject(object, (key, value) -> key.equals("drop")));

        Iterator<String> keys = node.keySetInObject(object).iterator();
        keys.next();
        keys.remove();
        Map.Entry<String, Object> entry = node.entrySetInObject(object).iterator().next();
        Object old = entry.getValue();
        assertSame(old, entry.setValue(null));
        assertSame(NullNode.instance, object.get(entry.getKey()));
        assertThrows(NodeException.class, () -> entry.setValue("invalid"));
        assertTrue(node.entrySetInObject(object).remove(entry));
        assertEquals(0, object.size());
    }

    @Test
    void supportsIndexesAccessAndMutations() throws Exception {
        ExternalNode<JsonNode> node = node();
        ArrayNode array = array("[\"zero\",\"one\"]");
        assertSame(array.get(1), node.getInArray(array, -1));
        assertNull(node.getInArray(array, -3));
        Nodes.Access access = new Nodes.Access();
        node.getAccessInArray(array, -1, access);
        assertTrue(access.present);
        node.putAccessInArray(array, array.size(), access);
        assertTrue(access.puttable);
        assertNull(access.node);
        node.putAccessInArray(array, 3, access);
        assertFalse(access.puttable);
        node.putAccessInArray(array, null, access);
        assertTrue(access.puttable);

        assertSame(array.get(1), node.setInArray(array, -1, null));
        node.addInArray(array, null);
        node.addInArray(array, -1, JsonNodeFactory.instance.numberNode(2));
        assertSame(NullNode.instance, array.get(1));
        assertNotNull(node.removeInArray(array, -1));
        assertThrows(NodeException.class, () -> node.setInArray(array, 9, NullNode.instance));
        assertThrows(NodeException.class, () -> node.addInArray(array, 9, NullNode.instance));
        assertThrows(NodeException.class, () -> node.removeInArray(array, 9));
        assertThrows(NodeException.class, () -> node.addInArray(array, "invalid"));

        ObjectNode object = object("{\"nil\":null}");
        node.getAccessInObject(object, "nil", access);
        assertTrue(access.present);
        assertSame(NullNode.instance, access.node);
        node.getAccessInObject(object, "missing", access);
        assertFalse(access.present);
        node.putInObject(object, "value", null);
        assertSame(NullNode.instance, object.get("value"));
        assertSame(NullNode.instance, node.removeInObject(object, "value"));
    }

    @Test
    void copiesFactoriesAndJsonPathKeepNativeNodes() throws Exception {
        ObjectNode object = object("{\"child\":{}}");
        ArrayNode array = array("[[]]");
        ObjectNode objectCopy = Nodes.copy(object);
        ArrayNode arrayCopy = Nodes.copy(array);
        assertNotSame(object, objectCopy);
        assertSame(object.get("child"), objectCopy.get("child"));
        assertNotSame(array, arrayCopy);
        assertSame(array.get(0), arrayCopy.get(0));

        JsonNodeFactory exactFactory = new JsonNodeFactory(true);
        ObjectNode exactObjectCopy = Nodes.copy(exactFactory.objectNode());
        ArrayNode exactArrayCopy = Nodes.copy(exactFactory.arrayNode());
        assertEquals(new BigDecimal("1.0"),
                exactObjectCopy.numberNode(new BigDecimal("1.0")).decimalValue());
        assertEquals(new BigDecimal("1.0"),
                exactArrayCopy.numberNode(new BigDecimal("1.0")).decimalValue());

        assertTrue(Nodes.createObjectNode(JsonNode.class) instanceof ObjectNode);
        assertTrue(Nodes.createArrayNode(JsonNode.class) instanceof ArrayNode);

        JsonPath.parse("$.a.b").ensurePut(object, JsonNodeFactory.instance.textNode("value"));
        JsonPath.parse("$.items[+].value").ensurePut(object, JsonNodeFactory.instance.textNode("item"));
        assertTrue(object.get("a") instanceof ObjectNode);
        assertTrue(object.get("items") instanceof ArrayNode);
        ObjectNode nullObject = object("{\"a\":null}");
        JsonPath.parse("$.a.b").ensurePut(nullObject, JsonNodeFactory.instance.textNode("value"));
        assertTrue(nullObject.get("a") instanceof ObjectNode);
        nullObject.set("value", NullNode.instance);
        assertNull(JsonPath.parse("$.value").ensurePutIfAbsent(
                nullObject, JsonNodeFactory.instance.textNode("replacement")));
        assertEquals("replacement", nullObject.get("value").textValue());
    }

    @Test
    void coreOperationsPreserveNativeJsonSemantics() throws Exception {
        ObjectNode number = JsonNodeFactory.instance.objectNode().put("value", 1);
        assertTrue(Nodes.equals(number.get("value"), 1));
        assertEquals(Nodes.hash(number.get("value")), Nodes.hash(1));

        ObjectNode source = object("{\"child\":{\"value\":1}}");
        ObjectNode copy = Sjf4j.global().copyNode(source);
        ((ObjectNode) copy.get("child")).put("value", 2);
        assertEquals(1, source.get("child").get("value").intValue());

        ObjectNode target = object("{\"a\":1,\"b\":2}");
        Patches.mergePatch(target, object("{\"a\":null}"));
        assertFalse(target.has("a"));
        assertEquals(2, target.get("b").intValue());
    }

    @SuppressWarnings("unchecked")
    private static ExternalNode<JsonNode> node() {
        return (ExternalNode<JsonNode>) ExternalRegistry.resolve(JsonNode.class);
    }

    private static ObjectNode object(String json) throws Exception { return (ObjectNode) MAPPER.readTree(json); }
    private static ArrayNode array(String json) throws Exception { return (ArrayNode) MAPPER.readTree(json); }

    private static URL resourceRoot(URL resource) throws Exception {
        File root = new File(resource.toURI());
        for (int i = 0; i < 3; i++) root = root.getParentFile();
        return root.toURI().toURL();
    }
}
