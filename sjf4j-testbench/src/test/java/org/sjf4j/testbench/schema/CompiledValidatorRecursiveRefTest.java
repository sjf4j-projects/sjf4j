package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.ValidationException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Draft 2019-09 recursiveRef shares the existing scoped-reference compiler. */
class CompiledValidatorRecursiveRefTest {
    private static final String RECURSIVE = """
            {
              "$schema":"https://json-schema.org/draft/2019-09/schema",
              "$id":"https://example.test/compiled-recursive/tree",
              "$recursiveAnchor":true,
              "type":"object",
              "properties":{
                "value":{"type":"integer"},
                "child":{"$recursiveRef":"#"}
              }
            }
            """;

    private static final String STATIC = """
            {
              "$schema":"https://json-schema.org/draft/2019-09/schema",
              "$id":"https://example.test/compiled-recursive/static",
              "type":"object",
              "properties":{
                "value":{"type":"integer"},
                "child":{"$recursiveRef":"#"}
              }
            }
            """;

    private static final String WRAPPER = """
            {
              "$schema":"https://json-schema.org/draft/2019-09/schema",
              "$id":"https://example.test/compiled-recursive/wrapper",
              "$recursiveAnchor":true,
              "type":"object",
              "properties":{"requiredOnEveryLevel":{"type":"integer"}},
              "required":["requiredOnEveryLevel"],
              "allOf":[{
                "$ref":"#/$defs/generic"
              }],
              "$defs":{
                "generic":{
                  "$id":"https://example.test/compiled-recursive/generic",
                  "$recursiveAnchor":true,
                  "type":"object",
                  "properties":{"child":{"$recursiveRef":"#"}}
                }
              }
            }
            """;

    private static final String ADJACENT = """
            {
              "$schema":"https://json-schema.org/draft/2019-09/schema",
              "$id":"https://example.test/compiled-recursive/adjacent",
              "$recursiveAnchor":true,
              "$recursiveRef":"#",
              "type":"object"
            }
            """;

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(RECURSIVE)
        boolean recursive(Object value);

        @ValidJsonSchema(STATIC)
        boolean staticReference(Object value);

        @ValidJsonSchema(WRAPPER)
        boolean scopedOverride(Object value);

        @ValidJsonSchema(RECURSIVE)
        void requireRecursive(Object value);
    }

    @Test
    void recursiveRefUsesLexicalAndDynamicScopes() {
        Direct direct = CompiledInstances.create(Direct.class);
        assertParity(RECURSIVE, direct::recursive,
                Map.of(), Map.of("value", 1), Map.of("value", "bad"),
                Map.of("value", 1, "child", Map.of("value", 2)),
                Map.of("value", 1, "child", Map.of("value", "bad")),
                Map.of("child", Map.of("child", Map.of("value", 3))),
                Map.of("child", 1), null);
        assertParity(STATIC, direct::staticReference,
                Map.of(), Map.of("value", 1, "child", Map.of("value", 2)),
                Map.of("value", 1, "child", Map.of("value", "bad")),
                Map.of("child", "bad"), null);
        assertParity(WRAPPER, direct::scopedOverride,
                Map.of("requiredOnEveryLevel", 1),
                Map.of("requiredOnEveryLevel", 1,
                        "child", Map.of("requiredOnEveryLevel", 2)),
                Map.of("requiredOnEveryLevel", 1,
                        "child", Map.of("child", Map.of("requiredOnEveryLevel", 2))),
                Map.of("requiredOnEveryLevel", 1, "child", 4),
                Map.of("child", Map.of("requiredOnEveryLevel", 2)), null);
    }

    @Test
    void recursiveReferencePreservesNestedDiagnostic() {
        Direct direct = CompiledInstances.create(Direct.class);
        ValidationException error = assertThrows(ValidationException.class,
                () -> direct.requireRecursive(Map.of("child", Map.of("value", "wrong"))));
        assertEquals("/child/value", error.getResult().getLastError()
                .getInstancePs().rootedPointerExpr());
        assertTrue(error.getResult().getLastError().getKeywordPs()
                .rootedPointerExpr().endsWith("/type"));
    }

    private static void assertParity(String schema, java.util.function.Predicate<Object> compiled,
                                     Object... values) {
        SchemaPlan runtime = JsonSchema.fromJson(schema).createPlan();
        for (Object value : values) {
            assertEquals(runtime.isValid(value), compiled.test(value),
                    "schema: " + schema + " instance: " + value);
        }
    }
}
