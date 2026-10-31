package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reuse must not leak diagnostics, input objects, or evaluated/ignore state. */
class ValidationContextReuseTest {

    @Test
    void reusableBooleanContextCanValidateDifferentValuesAndPlans() {
        SchemaPlan required = JsonSchema.fromJson(
                "{\"type\":\"object\",\"required\":[\"x\"],\"properties\":{\"x\":{\"type\":\"integer\"}}}")
                .createPlan();
        SchemaPlan string = JsonSchema.fromJson("{\"type\":\"string\",\"minLength\":2}")
                .createPlan();
        ValidationContext context = new ValidationContext();

        for (int i = 0; i < 25; i++) {
            assertTrue(required.isValid(Map.of("x", i), context));
            assertFalse(required.isValid(Map.of(), context));
            assertFalse(required.isValid(Map.of("x", "wrong"), context));
            assertTrue(string.isValid("good", context));
            assertFalse(string.isValid("x", context));
        }
        // Switching modes is safe: call semantics belong to SchemaPlan methods.
        assertFalse(required.validate(Map.of(), context).isValid());
        assertFalse(required.validate(Map.of("x", "wrong"), true, false, context).isValid());
        assertTrue(required.isValid(Map.of("x", 1), context));
    }

    @Test
    void reusableAggregateContextKeepsPriorResultsUnchanged() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"type\":\"object\",\"required\":[\"x\",\"y\"],"
                        + "\"properties\":{\"x\":{\"type\":\"integer\"},\"y\":{\"type\":\"string\"}}}")
                .createPlan();
        ValidationContext context = new ValidationContext();

        ValidationResult first = plan.validate(Map.of("x", "wrong"), context);
        assertFalse(first.isValid());
        assertTrue(first.getErrors().size() >= 2);
        int count = first.count();
        String error = first.getErrors().get(0).toString();

        ValidationResult valid = plan.validate(Map.of("x", 1, "y", "good"), context);
        assertTrue(valid.isValid());
        assertSame(ValidationResult.SUCCESS, valid);
        assertEquals(count, first.count());
        assertEquals(error, first.getErrors().get(0).toString());

        ValidationResult second = plan.validate(Map.of("x", 2), context);
        assertFalse(second.isValid());
        assertEquals(1, second.getErrors().size());
        assertEquals(count, first.count());
        assertNotSame(first.getMessages(), second.getMessages());
    }

    @Test
    void reusableContextPreservesScopeAndIgnoredProbeIsolation() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"properties\":{\"x\":true},\"not\":{\"allOf\":[false]},"
                        + "\"unevaluatedProperties\":false}")
                .createPlan();
        ValidationContext context = new ValidationContext();
        for (int i = 0; i < 10; i++) {
            assertFalse(plan.validate(Map.of("unknown", i), context).isValid());
            assertTrue(plan.validate(Map.of("x", i), context).isValid());
            assertTrue(plan.isValid(Map.of("x", i), context));
            assertFalse(plan.isValid(Map.of("unknown", i), context));
        }
    }

    @Test
    void reusableFailFastContextAndBooleanSchemasResetCleanly() {
        SchemaPlan required = JsonSchema.fromJson("{\"required\":[\"name\"]}").createPlan();
        SchemaPlan alwaysFalse = JsonSchema.fromJson("false").createPlan();
        SchemaPlan alwaysTrue = JsonSchema.fromJson("true").createPlan();
        ValidationContext ctx = new ValidationContext();

        ValidationResult failure = required.validate(Map.of(), ctx);
        assertEquals(1, failure.getErrors().size());
        assertThrows(ValidationException.class, () -> required.requireValid(Map.of(), ctx));
        assertTrue(required.validate(java.util.Collections.singletonMap("name", null), ctx).isValid());
        assertTrue(alwaysTrue.validate(Map.of(), ctx).isValid());
        assertFalse(alwaysFalse.validate(Map.of(), ctx).isValid());
        assertEquals(1, failure.count());
        assertFalse(alwaysFalse.isValid(Map.of(), ctx));
        assertTrue(alwaysTrue.isValid(Map.of(), ctx));
        assertEquals(1, failure.count());
        assertFalse(required.isValid(Map.of(), ctx));
        assertTrue(required.isValid(java.util.Collections.singletonMap("name", null), ctx));
    }
}
