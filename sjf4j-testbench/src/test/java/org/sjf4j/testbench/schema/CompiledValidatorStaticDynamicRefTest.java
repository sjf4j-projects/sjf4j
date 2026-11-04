package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompiledValidatorStaticDynamicRefTest {
    private static final String PLAIN_ANCHOR = """
            {"$defs":{"target":{"$anchor":"value","type":"string"}},
             "$dynamicRef":"#value"}
            """;
    private static final String POINTER_TO_DYNAMIC_ANCHOR = """
            {"$defs":{"target":{"$dynamicAnchor":"value","type":"string"}},
             "$dynamicRef":"#/$defs/target"}
            """;
    private static final String NONMATCHING_DYNAMIC_ANCHOR = """
            {"$defs":{"target":{"$anchor":"value","$dynamicAnchor":"other","type":"integer"}},
             "$dynamicRef":"#value"}
            """;
    private static final String BOOLEAN_POINTER = """
            {"$defs":{"false":false},"$dynamicRef":"#/$defs/false"}
            """;
    private static final String NESTED_POINTER = """
            {"properties":{"value":{"$dynamicRef":"#/$defs/target"}},
             "$defs":{"target":{"$dynamicAnchor":"value","type":"string"}}}
            """;

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(PLAIN_ANCHOR) boolean plain(Object value);
        @ValidJsonSchema(POINTER_TO_DYNAMIC_ANCHOR) boolean pointer(Object value);
        @ValidJsonSchema(NONMATCHING_DYNAMIC_ANCHOR) boolean nonmatching(Object value);
        @ValidJsonSchema(BOOLEAN_POINTER) boolean alwaysFalse(Object value);
        @ValidJsonSchema(NESTED_POINTER) boolean nested(Object value);
    }

    @Test
    void staticallyResolvedDynamicReferencesMatchRuntime() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan plain = JsonSchema.fromJson(PLAIN_ANCHOR).createPlan();
        SchemaPlan pointer = JsonSchema.fromJson(POINTER_TO_DYNAMIC_ANCHOR).createPlan();
        SchemaPlan nonmatching = JsonSchema.fromJson(NONMATCHING_DYNAMIC_ANCHOR).createPlan();
        SchemaPlan booleanPointer = JsonSchema.fromJson(BOOLEAN_POINTER).createPlan();
        SchemaPlan nested = JsonSchema.fromJson(NESTED_POINTER).createPlan();
        for (Object value : Arrays.asList(null, 1, "ok", "",
                List.of("ok"), Map.of(), Map.of("value", "ok"),
                Map.of("value", 1), Map.of("other", 1))) {
            assertEquals(plain.isValid(value), direct.plain(value), String.valueOf(value));
            assertEquals(pointer.isValid(value), direct.pointer(value), String.valueOf(value));
            assertEquals(nonmatching.isValid(value), direct.nonmatching(value), String.valueOf(value));
            assertEquals(booleanPointer.isValid(value), direct.alwaysFalse(value), String.valueOf(value));
            assertEquals(nested.isValid(value), direct.nested(value), String.valueOf(value));
        }
    }
}
