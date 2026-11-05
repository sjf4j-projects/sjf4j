package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NodeMapperRawValueTest {
    @Test
    void rawValueInObjectEncodesOnlyTheSelectedValue() {
        Instant instant = Instant.ofEpochMilli(2_000);
        LocalDate day = LocalDate.of(2024, 1, 2);
        RawBean bean = new RawBean();
        bean.instant = instant;
        bean.day = day;
        bean.child = new PlainBean();
        bean.map = new LinkedHashMap<>(Map.of("nested", bean.child));
        bean.list = List.of(bean.child);
        bean.bound = new BoundValue("raw");
        bean.mapValue = new EncodedMapValue(Map.of("nested", 1));
        bean.listValue = new EncodedListValue(List.of(1, 2));

        assertSame(instant, NodeWalker.getInObject(bean, "instant"));
        assertEquals(2_000L, NodeMapper.rawValueInObject(bean, "instant", RuntimeContext.EMPTY));
        assertEquals("2024-01-02", NodeMapper.rawValueInObject(bean, "day", RuntimeContext.EMPTY));
        assertEquals("raw", NodeMapper.rawValueInObject(bean, "bound", RuntimeContext.EMPTY));
        assertSame(bean.child, NodeMapper.rawValueInObject(bean, "child", RuntimeContext.EMPTY));
        assertSame(bean.map, NodeMapper.rawValueInObject(bean, "map", RuntimeContext.EMPTY));
        assertSame(bean.list, NodeMapper.rawValueInObject(bean, "list", RuntimeContext.EMPTY));
        assertSame(bean.mapValue.raw, NodeMapper.rawValueInObject(bean, "mapValue", RuntimeContext.EMPTY));
        assertSame(bean.listValue.raw, NodeMapper.rawValueInObject(bean, "listValue", RuntimeContext.EMPTY));
        assertEquals(JsonType.UNKNOWN, JsonType.of(bean.mapValue));
        assertEquals(JsonType.OBJECT, JsonType.of(bean.mapValue.raw));
        assertNull(NodeMapper.rawValueInObject(bean, "missing", RuntimeContext.EMPTY));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("instant", instant);
        map.put("child", bean.child);
        map.put("mapValue", bean.mapValue);
        map.put("nullValue", null);
        assertEquals(instant.toString(), NodeMapper.rawValueInObject(map, "instant", RuntimeContext.EMPTY));
        assertSame(bean.child, NodeMapper.rawValueInObject(map, "child", RuntimeContext.EMPTY));
        assertSame(bean.mapValue.raw, NodeMapper.rawValueInObject(map, "mapValue", RuntimeContext.EMPTY));
        assertNull(NodeMapper.rawValueInObject(map, "nullValue", RuntimeContext.EMPTY));

        JsonObject plain = JsonObject.of("instant", instant, "child", bean.child);
        assertEquals(instant.toString(), NodeMapper.rawValueInObject(plain, "instant", RuntimeContext.EMPTY));
        assertSame(bean.child, NodeMapper.rawValueInObject(plain, "child", RuntimeContext.EMPTY));

        RawJojo jojo = new RawJojo();
        jojo.instant = instant;
        jojo.put("dynamic", day);
        assertEquals(2_000L, NodeMapper.rawValueInObject(jojo, "instant", RuntimeContext.EMPTY));
        assertEquals("2024-01-02", NodeMapper.rawValueInObject(jojo, "dynamic", RuntimeContext.EMPTY));
        assertNull(NodeMapper.rawValueInObject(jojo, "missing", RuntimeContext.EMPTY));
        RuntimeContext epochDefault = new RuntimeContext(Map.of(Instant.class, "epochMillis"));
        assertEquals(2_000L, NodeMapper.rawValueInObject(map, "instant", epochDefault));
        assertEquals(2_000L, NodeMapper.rawValueInObject(plain, "instant", epochDefault));
        assertEquals(2_000L, NodeMapper.rawValueInObject(bean, "instant", epochDefault));
        assertEquals("2024-01-02", NodeMapper.rawValueInObject(bean, "day", epochDefault));

        assertSame(bean.mapValue, NodeMapper.scalarValueInObject(bean, "mapValue", RuntimeContext.EMPTY));
        assertSame(bean.listValue, NodeMapper.scalarValueInObject(bean, "listValue", RuntimeContext.EMPTY));
        assertSame(bean.mapValue, NodeMapper.scalarValueInObject(map, "mapValue", RuntimeContext.EMPTY));
        assertEquals(2_000L, NodeMapper.scalarValueInObject(bean, "instant", RuntimeContext.EMPTY));
        assertEquals(instant.toString(), NodeMapper.scalarValueInObject(map, "instant", RuntimeContext.EMPTY));
        assertEquals(2_000L, NodeMapper.scalarValueInObject(map, "instant", epochDefault));
    }

    static class RawBean {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant instant;
        public LocalDate day;
        public PlainBean child;
        public Map<String, Object> map;
        public List<PlainBean> list;
        public BoundValue bound;
        public EncodedMapValue mapValue;
        public EncodedListValue listValue;
    }

    static class RawJojo extends JsonObject {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant instant;
    }

    @NodeValue
    static class EncodedMapValue {
        final Map<String, Object> raw;
        EncodedMapValue(Map<String, Object> raw) { this.raw = raw; }
        @ValueToRaw Map<String, Object> toRaw() { return raw; }
        @RawToValue static EncodedMapValue fromRaw(Map<String, Object> raw) {
            return new EncodedMapValue(raw);
        }
    }

    @NodeValue
    static class EncodedListValue {
        final List<Object> raw;
        EncodedListValue(List<Object> raw) { this.raw = raw; }
        @ValueToRaw List<Object> toRaw() { return raw; }
        @RawToValue static EncodedListValue fromRaw(List<Object> raw) {
            return new EncodedListValue(raw);
        }
    }


    @NodeValue
    static class BoundValue {
        final String raw;
        BoundValue(String raw) { this.raw = raw; }
        @ValueToRaw String toRaw() { return raw; }
        @RawToValue static BoundValue fromRaw(String raw) { return new BoundValue(raw); }
    }

    static class PlainBean {
        public String name = "plain";
    }

}
