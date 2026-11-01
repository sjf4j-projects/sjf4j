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

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runtime logical values must be normalized before structural Nodes checks. */
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

    @CompiledValidator(fallback = false)
    public interface Validator {
        @ValidJsonSchema(SCHEMA)
        boolean object(Object value);

        @ValidJsonSchema(SCHEMA)
        boolean wrapped(MapValue value);
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
        }
    }
}
