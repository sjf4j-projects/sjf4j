package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.SchemaException;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaPlanTest {

    @Test
    void objectSchemaPlanSnapshotsMinimalRootRuntimeState() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{\"$id\":\"defs/user.json\",\"type\":\"string\"}");
        schema.setRetrievalUri(URI.create("file:///schemas/root.json"));

        SchemaPlan plan = schema.createPlan();

        assertEquals("file:///schemas/root.json", schema.getRetrievalUri().toString());
        assertEquals("defs/user.json", schema.getCanonicalUri().toString());
        assertTrue(plan.validate("hello").isValid());
        assertFalse(plan.validate(123).isValid());
    }

    @Test
    void booleanSchemaPlanStaysMinimal() {
        SchemaPlan plan = BooleanSchema.FALSE.createPlan();

        assertFalse(plan.isValid("any"));
    }

    @Test
    void planRefUsesPreResolvedTarget() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$defs\":{\"s\":{\"type\":\"string\"}}," +
                "\"$ref\":\"#/$defs/s\"" +
                "}");

        SchemaPlan plan = schema.createPlan();
        assertTrue(plan.isValid("ok"));
        assertFalse(plan.isValid(1));
    }

    @Test
    void planHandlesSelfRefWithoutPlaceholderBreakage() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{\"$ref\":\"#\"}");

        SchemaPlan plan = schema.createPlan();

        assertThrows(SchemaException.class, () -> plan.validate("Cyclic schema reference"));
    }
}
