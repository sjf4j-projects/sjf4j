package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A logical NodeValue is atomic even when its raw encoding is a container. */
public class CompiledValidatorValueBindingTest {
    private static final String SCHEMA =
            "{\"type\":\"object\",\"maxProperties\":1,\"required\":[\"name\"],"
            + "\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}";

    @NodeValue
    public static final class MapValue {
        private final Map<String, Object> raw;

        public MapValue(Map<String, Object> raw) {
            this.raw = raw;
        }

        @RawToValue
        public static MapValue fromRaw(Map<String, Object> raw) {
            return new MapValue(raw);
        }

        @ValueToRaw
        public Map<String, Object> toRaw() {
            return raw;
        }
    }

    @NodeValue
    public static final class ListValue {
        @ValueToRaw
        public List<Object> toRaw() { return List.of("abc"); }

        @RawToValue
        public static ListValue fromRaw(List<Object> raw) { return new ListValue(); }
    }

    public static class Holder {
        private final Object items;
        public Holder(Object items) { this.items = items; }
        public Object getItems() { return items; }
    }

    @CompiledValidator(fallback = false)
    public interface Validator {
        @ValidJsonSchema(SCHEMA)
        boolean object(Object value);

        @ValidJsonSchema(SCHEMA)
        boolean wrapped(MapValue value);

        @ValidJsonSchema("{\"required\":[\"name\"]}")
        boolean untypedRequired(Object value);

        @ValidJsonSchema("{\"type\":\"array\",\"minItems\":1}")
        boolean array(Object value);

        @ValidJsonSchema("{\"type\":\"object\",\"properties\":"
                + "{\"items\":{\"type\":\"array\",\"minItems\":1}}}")
        boolean nested(Holder value);
    }

    @Test
    void normalizesObjectAndDeclaredValueBindings() {
        Validator compiled = CompiledInstances.create(Validator.class);
        for (Map<String, Object> raw : new Map[]{
                Map.of("name", "Alice"),
                Map.of("name", "x"),
                Map.of("missing", 1),
                Map.of("name", "Alice", "extra", 1),
                new LinkedHashMap<String, Object>()
        }) {
            MapValue value = new MapValue(raw);
            boolean expected = JsonSchema.fromJson(SCHEMA).createPlan().isValid(value);
            assertEquals(expected, compiled.object(value));
            assertEquals(expected, compiled.wrapped(value));
            // The Map encoding is a leaf representation, not traversable OBNT.
            assertEquals(false, expected);
        }
    }

    @Test
    void valueBindingsRemainLeavesEvenWithContainerRawEncodings() {
        Validator compiled = CompiledInstances.create(Validator.class);
        MapValue wrapped = new MapValue(Map.of("name", "Alice"));
        MapValue missing = new MapValue(Map.of("missing", 1));

        // Raw Map itself is traversable; a Map-valued @NodeValue is not.
        assertEquals(true, compiled.object(Map.of("name", "Alice")));
        assertEquals(false, compiled.object(wrapped));
        assertEquals(true, compiled.untypedRequired(wrapped));
        assertEquals(true, compiled.untypedRequired(missing));
        assertEquals(false, compiled.untypedRequired(Map.of("missing", 1)));

        ListValue listValue = new ListValue();
        assertEquals(true, compiled.array(List.of("abc")));
        assertEquals(false, compiled.array(listValue));
        assertEquals(JsonSchema.fromJson("{\"type\":\"array\",\"minItems\":1}")
                .createPlan().isValid(listValue), compiled.array(listValue));
        assertEquals(true, compiled.nested(new Holder(List.of("abc"))));
        assertEquals(false, compiled.nested(new Holder(listValue)));
    }
}
