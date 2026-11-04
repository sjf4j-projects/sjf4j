package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationOptionsTest {
    @Test
    void defaultsPreserveExistingValidationBehavior() {
        ValidationOptions options = ValidationOptions.builder().build();
        assertFalse(options.isFailFast());
        assertFalse(options.isStrictFormat());
        assertFalse(options.isCollectAnnotations());

        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"integer\"}").createPlan();
        assertEquals(plan.validate("not an integer").isValid(),
                plan.validate("not an integer", options).isValid());
        assertEquals(plan.validate("not an integer").count(),
                plan.validate("not an integer", options).count());
        assertTrue(plan.validate(12, options).isValid());
    }

    @Test
    void flagsAreForwardedWithoutChangingLegacyCalls() {
        ValidationOptions options = ValidationOptions.builder()
                .failFast(true)
                .strictFormat(true)
                .build();
        assertTrue(options.isFailFast());
        assertTrue(options.isStrictFormat());

        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"string\",\"minLength\":3}").createPlan();
        assertEquals(plan.validate(1, true, true).isValid(),
                plan.validate(1, options).isValid());

        ValidationContext ctx = new ValidationContext(options, false);
        assertTrue(ctx.isFailFast());
        assertTrue(ctx.isStrictFormat());
        assertFalse(ctx.isCollectAnnotations());
    }

    @Test
    void annotationCollectionCanBeRequested() {
        ValidationOptions options = ValidationOptions.builder()
                .collectAnnotations(true)
                .build();
        assertTrue(options.isCollectAnnotations());
        SchemaPlan plan = JsonSchema.fromJson("{\"title\":\"Example\"}").createPlan();
        ValidationResult result = plan.validate(42, options);
        assertTrue(result.isValid());
        assertEquals(1, result.getAnnotations().size());
        assertEquals("title", result.getAnnotations().get(0).getKeyword());
        assertEquals("Example", result.getAnnotations().get(0).getValue());
        assertTrue(new ValidationContext(options, false).isCollectAnnotations());
        assertThrows(NullPointerException.class, () -> plan.validate(42, (ValidationOptions) null));
    }
}
