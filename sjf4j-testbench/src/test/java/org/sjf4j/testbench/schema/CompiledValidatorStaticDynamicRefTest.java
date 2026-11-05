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

    private static final String MULTIPLE_PATHS = """
            {
              "$id":"https://test.json-schema.org/dynamic-ref-with-multiple-paths/main",
              "if":{"properties":{"kindOfList":{"const":"numbers"}},"required":["kindOfList"]},
              "then":{"$ref":"numberList"},
              "else":{"$ref":"stringList"},
              "$defs":{
                "genericList":{"$id":"genericList",
                  "properties":{"list":{"items":{"$dynamicRef":"#itemType"}}},
                  "$defs":{"defaultItemType":{"$dynamicAnchor":"itemType"}}},
                "numberList":{"$id":"numberList",
                  "$defs":{"itemType":{"$dynamicAnchor":"itemType","type":"number"}},
                  "$ref":"genericList"},
                "stringList":{"$id":"stringList",
                  "$defs":{"itemType":{"$dynamicAnchor":"itemType","type":"string"}},
                  "$ref":"genericList"}
              }
            }
            """;
    private static final String RECURSIVE = """
            {"$id":"https://example.test/compiled-dynamic-tree",
             "$dynamicAnchor":"node","type":"object",
             "properties":{"value":{"type":"integer"},"child":{"$dynamicRef":"#node"}}}
            """;

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(PLAIN_ANCHOR) boolean plain(Object value);
        @ValidJsonSchema(POINTER_TO_DYNAMIC_ANCHOR) boolean pointer(Object value);
        @ValidJsonSchema(NONMATCHING_DYNAMIC_ANCHOR) boolean nonmatching(Object value);
        @ValidJsonSchema(BOOLEAN_POINTER) boolean alwaysFalse(Object value);
        @ValidJsonSchema(NESTED_POINTER) boolean nested(Object value);
        @ValidJsonSchema(MULTIPLE_PATHS) boolean multiplePaths(Object value);
        @ValidJsonSchema(RECURSIVE) boolean recursive(Object value);
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
    @Test
    void sameDynamicRefCanCompileToDifferentTargetsBasedOnSchemaPath() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan plan = JsonSchema.fromJson(MULTIPLE_PATHS).createPlan();
        for (Object value : List.of(
                Map.of("kindOfList", "numbers", "list", List.of(1, 2.5)),
                Map.of("kindOfList", "numbers", "list", List.of("foo")),
                Map.of("kindOfList", "strings", "list", List.of("foo")),
                Map.of("kindOfList", "strings", "list", List.of(1.5)),
                Map.of("list", List.of("hi")),
                Map.of("list", List.of(1)),
                Map.of("list", List.of()))) {
            assertEquals(plan.isValid(value), direct.multiplePaths(value), String.valueOf(value));
        }
    }

    @Test
    void recursiveDynamicReferenceUsesOneCompiledContext() {
        Direct direct = CompiledInstances.create(Direct.class);
        SchemaPlan plan = JsonSchema.fromJson(RECURSIVE).createPlan();
        for (Object value : List.of(
                Map.of("value", 1),
                Map.of("value", 1, "child", Map.of("value", 2)),
                Map.of("value", 1, "child", Map.of("value", "bad")),
                Map.of("value", 1, "child", Map.of("child", Map.of("value", 3))),
                Map.of("value", 1, "child", "not-object"))) {
            assertEquals(plan.isValid(value), direct.recursive(value), String.valueOf(value));
        }
    }

}
