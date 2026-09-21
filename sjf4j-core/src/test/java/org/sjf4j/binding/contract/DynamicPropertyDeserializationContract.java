package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SJF4J NodeBinding dynamic-storage and StreamingContext null-inclusion behavior. */
public abstract class DynamicPropertyDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** No Jackson mapping: disabling NodeBinding readDynamic leaves undeclared input out of JsonObject storage. */
    @Test void testReadDynamicDisabled() {
        StaticRead bean = (StaticRead) binding(StreamingContext.EMPTY).readNode("{\"id\":1,\"extra\":2}", StaticRead.class);
        assertEquals(1, bean.id); assertNull(bean.getNode("extra"));
    }
    /** No Jackson mapping: disabling NodeBinding writeDynamic omits JsonObject dynamic storage on output. */
    @Test void testWriteDynamicDisabled() {
        StaticWrite bean = (StaticWrite) binding(StreamingContext.EMPTY).readNode("{\"id\":1,\"extra\":2}", StaticWrite.class);
        assertEquals(2, bean.getInt("extra"));
        Map<?, ?> output = (Map<?, ?>) binding(StreamingContext.EMPTY).readNode(binding(StreamingContext.EMPTY).writeNodeAsString(bean), Map.class);
        assertEquals(1, ((Number) output.get("id")).intValue()); assertEquals(1, output.size());
    }
    /** No Jackson mapping: StreamingContext's default includes null map values during serialization. */
    @Test void testNullMapValueIncludedByDefault() {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("keep", 1); value.put("drop", null);
        Map<?, ?> output = (Map<?, ?>) binding(StreamingContext.EMPTY).readNode(binding(StreamingContext.EMPTY).writeNodeAsString(value), Map.class);
        assertTrue(output.containsKey("drop")); assertNull(output.get("drop"));
    }
    /** No Jackson mapping: StreamingContext can omit null map values during serialization. */
    @Test void testNullMapValueOmittedWhenContextExcludesNulls() {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("keep", 1); value.put("drop", null);
        JsonBinder<?, ?> binder = binding(new StreamingContext(false));
        Map<?, ?> output = (Map<?, ?>) binder.readNode(binder.writeNodeAsString(value), Map.class);
        assertEquals(1, output.size()); assertEquals(1, ((Number) output.get("keep")).intValue());
    }
    @NodeObject(readDynamic = false) static class StaticRead extends JsonObject { public int id; }
    @NodeObject(writeDynamic = false) static class StaticWrite extends JsonObject { public int id; }
}
