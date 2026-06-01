package org.sjf4j.jdk17.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.path.CompiledPath;
import org.sjf4j.annotation.path.EnsurePutIfAbsentByPath;
import org.sjf4j.compiled.CompiledRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EnsurePutIfAbsentByPathTest {

    @Test
    public void writesAbsentAndNullButKeepsExistingValues() {
        EnsurePutIfAbsentNodes nodes = CompiledRegistry.of(EnsurePutIfAbsentNodes.class);
        Root root = new Root();

        assertNull(nodes.ensureMapValue(root, "first"));
        assertEquals("first", root.map.get("once"));
        assertEquals("first", nodes.ensureMapValue(root, "second"));
        assertEquals("first", root.map.get("once"));

        root.map.put("once", null);
        assertNull(nodes.ensureMapValue(root, "third"));
        assertEquals("third", root.map.get("once"));
    }

    @Test
    public void createsMissingParentsBeforeIfAbsentFinalWrite() {
        EnsurePutIfAbsentNodes nodes = CompiledRegistry.of(EnsurePutIfAbsentNodes.class);
        Root root = new Root();

        assertNull(nodes.ensureNestedMap(root, "nested"));
        assertEquals("nested", ((Map<?, ?>) root.map.get("a")).get("b"));

        assertNull(nodes.ensurePojo(root, "pojo"));
        assertEquals("pojo", root.bean.child.name);
        assertEquals("pojo", nodes.ensurePojo(root, "ignored"));
        assertEquals("pojo", root.bean.child.name);
    }

    @Test
    public void supportsIndexAndDynamicParamTargets() {
        EnsurePutIfAbsentNodes nodes = CompiledRegistry.of(EnsurePutIfAbsentNodes.class);
        Root root = new Root();

        assertNull(nodes.ensureListIndex(root, "zero"));
        assertEquals("zero", root.list.get(0));
        assertEquals("zero", nodes.ensureListIndex(root, "ignored"));
        assertEquals("zero", root.list.get(0));

        assertNull(nodes.ensureDynamicKey(root, "dyn", "dynamic"));
        assertEquals("dynamic", root.map.get("dyn"));
        assertEquals("dynamic", nodes.ensureDynamicKey(root, "dyn", "ignored"));

        assertNull(nodes.ensureDynamicIndex(root, 1, "one"));
        assertEquals("one", root.list.get(1));
        assertEquals("one", nodes.ensureDynamicIndex(root, 1, "ignored"));
    }

    @Test
    public void voidReturnDoesNotOverwriteExistingValue() {
        EnsurePutIfAbsentNodes nodes = CompiledRegistry.of(EnsurePutIfAbsentNodes.class);
        Root root = new Root();

        nodes.ensureVoid(root, "first");
        assertEquals("first", root.map.get("voidOnce"));
        nodes.ensureVoid(root, "second");
        assertEquals("first", root.map.get("voidOnce"));
    }

    @Test
    public void appendAlwaysAppendsAndReturnsNull() {
        EnsurePutIfAbsentNodes nodes = CompiledRegistry.of(EnsurePutIfAbsentNodes.class);
        Root root = new Root();

        assertNull(nodes.append(root, "a"));
        assertNull(nodes.append(root, "b"));
        assertEquals(List.of("a", "b"), root.list);
    }

    @Test
    public void supportsJsonObjectAndJsonArrayTargets() {
        EnsurePutIfAbsentNodes nodes = CompiledRegistry.of(EnsurePutIfAbsentNodes.class);
        Root root = new Root();

        assertNull(nodes.ensureJsonObject(root, "json-object"));
        assertEquals("json-object", root.jsonObject.getJsonObject("owner").getString("name"));
        assertEquals("json-object", nodes.ensureJsonObject(root, "ignored"));

        assertNull(nodes.ensureJsonArray(root, "json-array"));
        assertEquals("json-array", ((Map<?, ?>) root.jsonArray.getNode(0)).get("name"));
        assertEquals("json-array", nodes.ensureJsonArray(root, "ignored"));
    }

    @Test
    public void nullRootThrowsNullPointerException() {
        EnsurePutIfAbsentNodes nodes = CompiledRegistry.of(EnsurePutIfAbsentNodes.class);

        assertThrows(NullPointerException.class, () -> nodes.ensureMapValue(null, "x"));
    }

    static final class Root {
        public Map<String, Object> map = new HashMap<>();
        public List<String> list = new ArrayList<>();
        public Bean bean = new Bean();
        public JsonObject jsonObject = new JsonObject();
        public JsonArray jsonArray = new JsonArray();
    }

    static final class Bean {
        private Child child;
        public Child getChild() { return child; }
        public void setChild(Child child) { this.child = child; }
    }

    static final class Child {
        public String name;
    }

    @CompiledPath
    interface EnsurePutIfAbsentNodes {
        @EnsurePutIfAbsentByPath("$.map.once")
        Object ensureMapValue(Root root, Object value);

        @EnsurePutIfAbsentByPath("$.map.a.b")
        Object ensureNestedMap(Root root, Object value);

        @EnsurePutIfAbsentByPath("$.bean.child.name")
        String ensurePojo(Root root, String value);

        @EnsurePutIfAbsentByPath("$.list[0]")
        String ensureListIndex(Root root, String value);

        @EnsurePutIfAbsentByPath("$.map[{key}]")
        Object ensureDynamicKey(Root root, String key, Object value);

        @EnsurePutIfAbsentByPath("$.list[{idx}]")
        String ensureDynamicIndex(Root root, int idx, String value);

        @EnsurePutIfAbsentByPath("$.map.voidOnce")
        void ensureVoid(Root root, Object value);

        @EnsurePutIfAbsentByPath("$.list[+]")
        String append(Root root, String value);

        @EnsurePutIfAbsentByPath("$.jsonObject.owner.name")
        Object ensureJsonObject(Root root, String value);

        @EnsurePutIfAbsentByPath("$.jsonArray[0].name")
        Object ensureJsonArray(Root root, String value);
    }
}
