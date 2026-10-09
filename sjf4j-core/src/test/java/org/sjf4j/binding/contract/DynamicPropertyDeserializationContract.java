package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.binding.Binder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SJF4J NodeBinding dynamic-storage and StreamingContext null-inclusion behavior. */
public abstract class DynamicPropertyDeserializationContract {
    protected abstract Binder<?, ?> binding(RuntimeContext context);
    /** No Jackson mapping: disabling NodeBinding readDynamic leaves undeclared input out of JsonObject storage. */
    @Test void testReadDynamicDisabled() {
        StaticRead bean = (StaticRead) binding(RuntimeContext.EMPTY).readNode("{\"id\":1,\"extra\":2}", StaticRead.class);
        assertEquals(1, bean.id); assertNull(bean.getNode("extra"));
    }
    /** No Jackson mapping: disabling NodeBinding writeDynamic omits JsonObject dynamic storage on output. */
    @Test void testWriteDynamicDisabled() {
        StaticWrite bean = (StaticWrite) binding(RuntimeContext.EMPTY).readNode("{\"id\":1,\"extra\":2}", StaticWrite.class);
        assertEquals(2, bean.getInt("extra"));
        Map<?, ?> output = (Map<?, ?>) binding(RuntimeContext.EMPTY).readNode(binding(RuntimeContext.EMPTY).writeNodeAsString(bean), Map.class);
        assertEquals(1, ((Number) output.get("id")).intValue()); assertEquals(1, output.size());
    }
    /** No Jackson mapping: StreamingContext's default includes null map values during serialization. */
    @Test void testNullMapValueIncludedByDefault() {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("keep", 1); value.put("drop", null);
        Map<?, ?> output = (Map<?, ?>) binding(RuntimeContext.EMPTY).readNode(binding(RuntimeContext.EMPTY).writeNodeAsString(value), Map.class);
        assertTrue(output.containsKey("drop")); assertNull(output.get("drop"));
    }
    /** No Jackson mapping: StreamingContext can omit null map values during serialization. */
    @Test void testNullMapValueOmittedWhenContextExcludesNulls() {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("keep", 1); value.put("drop", null);
        Binder<?, ?> binder = binding(new RuntimeContext(false));
        Map<?, ?> output = (Map<?, ?>) binder.readNode(binder.writeNodeAsString(value), Map.class);
        assertEquals(1, output.size()); assertEquals(1, ((Number) output.get("keep")).intValue());
    }
    /** Unknown fields before and after declared fields are bound in a single pass. */
    @Test void testJojoMixedStaticAndDynamicFields() {
        MixedRead value = (MixedRead) binding(RuntimeContext.EMPTY).readNode(
                "{\"before\":{\"nested\":[1,null,true]},\"id\":7,\"name\":\"Ada\",\"after\":null}",
                MixedRead.class);
        assertEquals(7, value.id);
        assertEquals("Ada", value.name);
        assertEquals(2, value.size() - 2);
        Map<?, ?> before = (Map<?, ?>) value.getNode("before");
        assertEquals(1, ((Number) ((java.util.List<?>) before.get("nested")).get(0)).intValue());
        assertNull(((java.util.List<?>) before.get("nested")).get(1));
        assertEquals(true, ((java.util.List<?>) before.get("nested")).get(2));
        assertTrue(value.containsKey("after"));
        assertNull(value.getNode("after"));
    }

    /** Known-only JOJOs should not eagerly create dynamic storage. */
    @Test void testJojoKnownOnlyDoesNotAllocateDynamicMap() {
        MixedRead value = (MixedRead) binding(RuntimeContext.EMPTY).readNode(
                "{\"id\":12,\"name\":\"known\"}", MixedRead.class);
        assertEquals(12, value.id);
        assertEquals("known", value.name);
        assertTrue(value.dynamicProperties().isEmpty());
    }

    /** A declared nested type must be bound as its target type, not left as a raw Map. */
    @Test void testJojoNestedDeclaredPojo() {
        NestedRead value = (NestedRead) binding(RuntimeContext.EMPTY).readNode(
                "{\"child\":{\"id\":5},\"extra\":{\"flag\":true}}", NestedRead.class);
        assertEquals(5, value.child.id);
        assertEquals(true, ((Map<?, ?>) value.getNode("extra")).get("flag"));
    }

    static class MixedRead extends JsonObject {
        public int id;
        public String name;
    }

    static class NestedRead extends JsonObject {
        public Child child;
    }

    static class Child {
        public int id;
    }

    @NodeObject(readDynamic = false) static class StaticRead extends JsonObject { public int id; }
    @NodeObject(writeDynamic = false) static class StaticWrite extends JsonObject { public int id; }
}
