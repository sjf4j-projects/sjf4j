package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A context belongs to one validation; nested frames restore enclosing state. */
class ValidationContextTest {

    @Test
    void failFastKeepsOneErrorWhenUnevaluatedKeywordsContinueEvaluation() {
        SchemaPlan objectPlan = JsonSchema.fromJson(
                "{\"required\":[\"x\"],\"unevaluatedProperties\":false}").createPlan();
        Map<String, Object> invalidObject = Map.of("extra", 1);

        ValidationResult failFastResult = objectPlan.validate(invalidObject, true, false);
        assertFalse(failFastResult.isValid());
        assertEquals(1, failFastResult.count());
        assertEquals("required", failFastResult.getLastError().getKeyword());

        ValidationResult aggregateResult = objectPlan.validate(invalidObject);
        assertFalse(aggregateResult.isValid());
        assertEquals(2, aggregateResult.count());

        SchemaPlan arrayPlan = JsonSchema.fromJson(
                "{\"minItems\":2,\"unevaluatedItems\":false}").createPlan();
        ValidationResult failFastArray = arrayPlan.validate(Collections.singletonList(1), true, false);
        assertFalse(failFastArray.isValid());
        assertEquals(1, failFastArray.count());
        assertEquals("minItems", failFastArray.getLastError().getKeyword());
        assertEquals(2, arrayPlan.validate(Collections.singletonList(1)).count());

    }

    @Test
    void failFastSkipsSubsequentErrorDetails() {
        ValidationContext ctx = new ValidationContext(true, false, false);
        org.sjf4j.path.PathSegment root = org.sjf4j.path.PathSegment.Root.INSTANCE;
        assertTrue(ctx.needsErrorDetail());

        ctx.addError(null, root, root, null, "required", "missing property");
        assertFalse(ctx.needsErrorDetail());
        ctx.addError(null, root, root, null, "type", "ignored");
        assertEquals(1, ctx.toResult(false).count());
    }

    @Test
    void unevaluatedFailFastRestoresEnclosingEvaluatedFrame() {
        String[] schemas = {
                "{\"unevaluatedProperties\":false}",
                "{\"unevaluatedItems\":false}"
        };
        Object[] nodes = {Map.of("extra", 1), List.of(1)};
        for (int i = 0; i < schemas.length; i++) {
            SchemaPlan plan = JsonSchema.fromJson(schemas[i]).createPlan();
            Instance instance = Instance.infer(nodes[i]);
            instance.createEvaluated();
            EvaluationBits enclosing = instance.peekEvaluated();
            ValidationContext ctx = new ValidationContext(true, false, false);

            assertFalse(plan.evaluate(instance, org.sjf4j.path.PathSegment.Root.INSTANCE, ctx));
            assertSame(enclosing, instance.peekEvaluated(),
                    "the failing unevaluated keyword must release its own frame");
            assertSame(enclosing, instance.popEvaluated());
            assertNull(instance.peekEvaluated());
            assertEquals(1, ctx.toResult(false).count());
        }
    }

    @Test
    void userValidationExceptionIsNotSwallowed() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"properties\":{\"x\":{\"type\":\"integer\"}}}").createPlan();
        ValidationException failure = ValidationException.failure("type", null, null,
                org.sjf4j.path.PathSegment.Root.INSTANCE);
        Map<String, Object> broken = new AbstractMap<String, Object>() {
            @Override
            public Set<Map.Entry<String, Object>> entrySet() {
                throw failure;
            }
        };

        assertSame(failure, assertThrows(ValidationException.class,
                () -> plan.validate(broken)));
    }

    @Test
    void probeAndDynamicScopeRestoreTheirFramesOnThrownException() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"$dynamicAnchor\":\"active\",\"anyOf\":[{\"required\":[\"x\"]},false]}"
        ).createPlan();
        ValidationContext ctx = new ValidationContext(false, false, false);
        Map<String, Object> broken = new AbstractMap<String, Object>() {
            @Override
            public Set<Map.Entry<String, Object>> entrySet() {
                throw new IllegalStateException("entrySet");
            }
        };

        // Calls the internal evaluator deliberately to test that the nested
        // frames restore themselves independently of the root entry point.
        assertThrows(IllegalStateException.class,
                () -> plan.evaluate(Instance.infer(broken),
                        org.sjf4j.path.PathSegment.Root.INSTANCE, ctx));
        assertTrue(ctx.needsErrorDetail());
        assertNotNull(ctx.planStack());
        assertTrue(ctx.planStack().isEmpty());
    }

}
