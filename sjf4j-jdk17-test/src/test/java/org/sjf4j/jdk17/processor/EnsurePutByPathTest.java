package org.sjf4j.jdk17.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.annotation.compiled.EnsurePutByPath;
import org.sjf4j.compiled.CompiledNodesRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EnsurePutByPathTest {

    @Test
    public void createsMissingMapListAndPojoParents() {
        EnsurePutNodes nodes = CompiledNodesRegistry.of(EnsurePutNodes.class);
        Root root = new Root();

        assertNull(nodes.ensureDefaultMap(root, "map-value"));
        assertInstanceOf(LinkedHashMap.class, root.map.get("a"));
        assertEquals("map-value", ((Map<?, ?>) root.map.get("a")).get("b"));
        assertEquals("map-value", nodes.ensureDefaultMap(root, "map-new"));
        assertEquals("map-new", ((Map<?, ?>) root.map.get("a")).get("b"));

        assertNull(nodes.ensurePojo(root, "pojo-value"));
        assertEquals("pojo-value", root.bean.child.name);

        assertNull(nodes.ensureListSlot(root, "list-value"));
        assertInstanceOf(LinkedHashMap.class, root.list.get(0));
        assertEquals("list-value", root.list.get(0).get("leaf"));
    }

    @Test
    public void preservesConcreteContainerTypesWhenCreatingParents() {
        EnsurePutNodes nodes = CompiledNodesRegistry.of(EnsurePutNodes.class);
        Root root = new Root();

        assertNull(nodes.ensureHashMap(root, "hash-value"));
        assertInstanceOf(HashMap.class, root.hashMap.get("typed"));
        assertEquals("hash-value", root.hashMap.get("typed").get("leaf"));

        assertNull(nodes.ensureLinkedList(root, "linked-value"));
        assertInstanceOf(LinkedList.class, root.linkedLists.get("typed"));
        assertEquals("linked-value", root.linkedLists.get("typed").get(0));
    }

    @Test
    public void createsJsonObjectAndJsonArrayParents() {
        EnsurePutNodes nodes = CompiledNodesRegistry.of(EnsurePutNodes.class);
        Root root = new Root();

        assertNull(nodes.ensureJsonObject(root, "json-object"));
        assertEquals("json-object", root.jsonObject.getJsonObject("owner").getString("name"));

        assertNull(nodes.ensureJsonArray(root, "json-array"));
        assertEquals("json-array", ((Map<?, ?>) root.jsonArray.getNode(0)).get("name"));
    }

    @Test
    public void supportsDynamicKeysIndexesAndMiddleAppend() {
        EnsurePutNodes nodes = CompiledNodesRegistry.of(EnsurePutNodes.class);
        Root root = new Root();

        assertNull(nodes.ensureDynamic(root, "region", 0, "dynamic-value"));
        assertEquals("dynamic-value", ((List<?>) root.map.get("region")).get(0));

        assertNull(nodes.ensureMiddleAppend(root, "first"));
        assertNull(nodes.ensureMiddleAppend(root, "second"));
        assertEquals("first", root.appendList.get(0).leaf);
        assertEquals("second", root.appendList.get(1).leaf);
    }

    @Test
    public void nullRootThrowsNullPointerException() {
        EnsurePutNodes nodes = CompiledNodesRegistry.of(EnsurePutNodes.class);

        assertThrows(NullPointerException.class, () -> nodes.ensureDefaultMap(null, "x"));
    }

    static final class Root {
        public Map<String, Object> map = new HashMap<>();
        public HashMap<String, HashMap<String, String>> hashMap = new HashMap<>();
        public Map<String, LinkedList<String>> linkedLists = new HashMap<>();
        public List<Map<String, Object>> list = new ArrayList<>();
        public List<AppendBean> appendList = new ArrayList<>();
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

    static final class AppendBean {
        public String leaf;
    }

    @CompiledNodes
    interface EnsurePutNodes {
        @EnsurePutByPath("$.map.a.b")
        Object ensureDefaultMap(Root root, Object value);

        @EnsurePutByPath("$.hashMap.typed.leaf")
        String ensureHashMap(Root root, String value);

        @EnsurePutByPath("$.linkedLists.typed[0]")
        String ensureLinkedList(Root root, String value);

        @EnsurePutByPath("$.bean.child.name")
        String ensurePojo(Root root, String value);

        @EnsurePutByPath("$.list[0].leaf")
        Object ensureListSlot(Root root, Object value);

        @EnsurePutByPath("$.jsonObject.owner.name")
        Object ensureJsonObject(Root root, String value);

        @EnsurePutByPath("$.jsonArray[0].name")
        Object ensureJsonArray(Root root, String value);

        @EnsurePutByPath("$.map[{key}][{idx}]")
        Object ensureDynamic(Root root, String key, int idx, Object value);

        @EnsurePutByPath("$.appendList[+].leaf")
        String ensureMiddleAppend(Root root, String value);
    }
}
