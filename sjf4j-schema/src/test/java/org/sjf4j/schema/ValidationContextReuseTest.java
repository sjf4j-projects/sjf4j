package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A context has fixed options and keeps diagnostics across independent evaluations. */
class ValidationContextReuseTest {

    @Test
    void booleanOnlyContextCanValidateDifferentInputsAndPlans() {
        SchemaPlan required = JsonSchema.fromJson(
                "{\"type\":\"object\",\"required\":[\"x\"],\"properties\":{\"x\":{\"type\":\"integer\"}}}")
                .createPlan();
        SchemaPlan string = JsonSchema.fromJson("{\"type\":\"string\",\"minLength\":2}").createPlan();
        ValidationContext ctx = new ValidationContext(true, false, true);

        for (int i = 0; i < 20; i++) {
            assertTrue(required.evaluate(Map.of("x", i), ctx));
            assertFalse(required.evaluate(Map.of(), ctx));
            assertFalse(required.evaluate(Map.of("x", "wrong"), ctx));
            assertTrue(string.evaluate("good", ctx));
            assertFalse(string.evaluate("x", ctx));
        }
        assertSame(ValidationResult.SUCCESS, ctx.toResult(true));
        assertEquals(0, ctx.toResult(false).count());
    }

    @Test
    void failFastDiagnosticsAccumulateAcrossEvaluations() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"required\":[\"x\",\"y\"],\"properties\":{\"x\":{\"type\":\"integer\"}}}")
                .createPlan();
        ValidationContext ctx = new ValidationContext(true, false, false);

        assertFalse(plan.evaluate(Map.of("x", "wrong"), ctx));
        assertEquals(1, ctx.toResult(false).getErrors().size());
        ValidationResult first = ctx.toResult(false);
        assertFalse(plan.evaluate(Map.of("x", 1), ctx));
        assertEquals(2, ctx.toResult(false).getErrors().size());
        assertSame(first.getErrors(), ctx.toResult(false).getErrors());

        // Callers explicitly control the lifetime of their accumulated diagnostics.
        ctx.clearErrors();
        assertTrue(first.getErrors().isEmpty());
        assertTrue(plan.evaluate(Map.of("x", 1, "y", 2), ctx));
        assertSame(ValidationResult.SUCCESS, ctx.toResult(true));
    }

    @Test
    void aggregateModeCollectsErrorsAcrossCalls() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"required\":[\"x\",\"y\"],\"properties\":{\"x\":{\"type\":\"integer\"}}}")
                .createPlan();
        ValidationContext ctx = new ValidationContext(false, false, false);
        boolean allValid = true;

        allValid &= plan.evaluate(Map.of("x", "wrong"), ctx);
        assertEquals(2, ctx.toResult(false).getErrors().size());
        allValid &= plan.evaluate(Map.of("x", 1), ctx);
        assertEquals(3, ctx.toResult(false).getErrors().size());
        assertFalse(allValid);
        assertFalse(ctx.toResult(allValid).isValid());
        assertEquals(3, ctx.toResult(allValid).count());
    }

    @Test
    void keepsDiagnosticPathsAndScopeIsolationAcrossCalls() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"properties\":{\"x\":true},\"not\":{\"allOf\":[false]},"
                + "\"unevaluatedProperties\":false}").createPlan();
        ValidationContext ctx = new ValidationContext(true, false, false);

        for (int i = 0; i < 10; i++) {
            assertFalse(plan.evaluate(Map.of("unknown", i), ctx));
            assertTrue(plan.evaluate(Map.of("x", i), ctx));
        }
        assertEquals(10, ctx.toResult(false).getErrors().size());
        for (ValidationResult.Error msg : ctx.toResult(false).getErrors()) {
            assertNotNull(msg.getInstancePs());
        }
        assertTrue(ctx.planStack() == null || ctx.planStack().isEmpty());
        assertNull(ctx.reusedLeaf().node());
    }

    @Test
    void strictFormatIsFixedAndBooleanSchemasAlsoAccumulateFailures() {
        SchemaPlan email = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"email\"}").createPlan();
        ValidationContext strict = new ValidationContext(true, true, false);
        ValidationContext permissive = new ValidationContext(true, false, false);

        assertFalse(email.evaluate("not-an-email", strict));
        assertTrue(email.evaluate("not-an-email", permissive));
        assertEquals(1, strict.toResult(false).getErrors().size());
        // Collection of annotations is deliberately deferred to a later change.
        assertSame(ValidationResult.SUCCESS, permissive.toResult(true));
        assertTrue(permissive.getAnnotations().isEmpty());

        SchemaPlan alwaysFalse = JsonSchema.fromJson("false").createPlan();
        SchemaPlan alwaysTrue = JsonSchema.fromJson("true").createPlan();
        assertFalse(alwaysFalse.evaluate(new Object(), strict));
        assertTrue(alwaysTrue.evaluate(new Object(), strict));
        assertFalse(alwaysFalse.evaluate(null, strict));
        assertEquals(3, strict.toResult(false).getErrors().size());
        assertEquals("false", strict.toResult(false).getLastError().getKeyword());
    }

    @Test
    void exceptionsDoNotRetainExecutionState() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"properties\":{\"x\":{\"type\":\"integer\"}}}").createPlan();
        ValidationContext ctx = new ValidationContext(false, false, false);
        Map<String, Object> broken = new AbstractMap<String, Object>() {
            @Override
            public Set<Map.Entry<String, Object>> entrySet() {
                throw new IllegalStateException("bad input");
            }
        };

        assertThrows(IllegalStateException.class, () -> plan.evaluate(broken, ctx));
        assertTrue(ctx.planStack() == null || ctx.planStack().isEmpty());
        assertNull(ctx.reusedLeaf().node());
        assertTrue(plan.evaluate(Map.of("x", 1), ctx));
        assertSame(ValidationResult.SUCCESS, ctx.toResult(true));
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
        // frames restore themselves, independently of the public reset().
        assertThrows(IllegalStateException.class,
                () -> plan.evaluate(Instance.infer(broken),
                        org.sjf4j.path.PathSegment.Root.INSTANCE, ctx));
        assertTrue(ctx.needsErrorDetail());
        assertNotNull(ctx.planStack());
        assertTrue(ctx.planStack().isEmpty());
    }

    @Test
    void evaluationReleasesTemporaryStateWithoutClearingDiagnostics() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"$dynamicAnchor\":\"root\",\"required\":[\"x\"]}").createPlan();
        ValidationContext ctx = new ValidationContext(true, false, false);

        assertFalse(plan.evaluate(Collections.emptyMap(), ctx));
        assertTrue(ctx.planStack().isEmpty());
        assertEquals(1, ctx.toResult(false).getErrors().size());

        ctx.pushIgnoreError();
        ctx.pushPlan(plan);
        ctx.reusedLeaf().reuse("retain-me", org.sjf4j.JsonType.STRING, false);

        assertTrue(plan.evaluate(Map.of("x", 1), ctx));
        assertTrue(ctx.planStack().isEmpty());
        assertNull(ctx.reusedLeaf().node());
        assertTrue(ctx.needsErrorDetail());
        assertEquals(1, ctx.toResult(false).getErrors().size());
    }
}
