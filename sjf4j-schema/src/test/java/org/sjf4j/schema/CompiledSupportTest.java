package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CompiledSupportTest {
    @Test
    void scalarMemberCodecRespectsPropertyAndContextFormats() {
        Instant instant = Instant.ofEpochMilli(2_000);
        RuntimeContext epochMillis = new RuntimeContext(Map.of(Instant.class, "epochMillis"));

        Event event = new Event();
        event.declared = instant;
        event.dynamic = instant;
        assertEquals(2_000L, CompiledSupport.scalarValueInObject(
                event, "declared", RuntimeContext.EMPTY));
        assertEquals(instant.toString(), CompiledSupport.scalarValueInObject(
                event, "dynamic", RuntimeContext.EMPTY));
        assertEquals(2_000L, CompiledSupport.scalarValueInObject(
                event, "dynamic", epochMillis));

        Map<String, Object> map = Map.of("when", instant);
        assertEquals(instant.toString(), CompiledSupport.scalarValueInObject(
                map, "when", RuntimeContext.EMPTY));
        assertEquals(2_000L, CompiledSupport.scalarValueInObject(
                map, "when", epochMillis));

        Jojo jojo = new Jojo();
        jojo.declared = instant;
        jojo.put("dynamic", instant);
        assertEquals(2_000L, CompiledSupport.scalarValueInObject(
                jojo, "declared", RuntimeContext.EMPTY));
        assertEquals(instant.toString(), CompiledSupport.scalarValueInObject(
                jojo, "dynamic", RuntimeContext.EMPTY));
        assertEquals(2_000L, CompiledSupport.scalarValueInObject(
                jojo, "dynamic", epochMillis));
        assertNull(CompiledSupport.scalarValueInObject(
                jojo, "missing", epochMillis));
    }

    @Test
    void objectAndArrayEncodedValuesRemainAtomic() {
        MapValue mapValue = new MapValue(Map.of("member", 1));
        ListValue listValue = new ListValue();
        Map<String, Object> values = Map.of("map", mapValue, "list", listValue);
        assertSame(mapValue, CompiledSupport.scalarValueInObject(
                values, "map", RuntimeContext.EMPTY));
        assertSame(listValue, CompiledSupport.scalarValueInObject(
                values, "list", RuntimeContext.EMPTY));

        Object plain = new Event();
        assertSame(plain, CompiledSupport.scalarValueInObject(
                Map.of("plain", plain), "plain", RuntimeContext.EMPTY));
    }

    static final class Event {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant declared;
        public Instant dynamic;
    }

    static final class Jojo extends JsonObject {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant declared;
    }

    @NodeValue
    public static final class MapValue {
        private final Map<String, Object> raw;
        public MapValue(Map<String, Object> raw) { this.raw = raw; }
        @ValueToRaw public Map<String, Object> toRaw() { return raw; }
        @RawToValue public static MapValue fromRaw(Map<String, Object> raw) {
            return new MapValue(raw);
        }
    }

    @NodeValue
    public static final class ListValue {
        @ValueToRaw public List<Object> toRaw() { return List.of(1); }
        @RawToValue public static ListValue fromRaw(List<Object> raw) {
            return new ListValue();
        }
    }
}
