package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CompiledSchemaSnapshotTest {

    @Test
    void resolvesChildPlanWithinFrozenRootResource() {
        CompiledSchemaSnapshot snapshot = CompiledSchemaSnapshot.create(
                new String[]{"sjf4j:/user/"},
                new String[]{"{\"type\":\"object\",\"properties\":{\"age\":{\"type\":\"integer\",\"minimum\":1}}}"}, 0);

        assertTrue(snapshot.root().isValid(Collections.singletonMap("age", 3)));
        assertFalse(snapshot.root().isValid(Collections.singletonMap("age", 0)));

        SchemaPlan age = snapshot.resolve("#/properties/age");
        assertTrue(age.isValid(3));
        assertFalse(age.isValid(0));
    }

    @Test
    void resolvesCrossDocumentRefUsingSameRegistry() {
        CompiledSchemaSnapshot snapshot = CompiledSchemaSnapshot.create(
                new String[]{"sjf4j:/root.json", "sjf4j:/defs.json"},
                new String[]{"{\"$ref\":\"defs.json#/$defs/positive\"}", "{\"$defs\":{\"positive\":{\"type\":\"integer\",\"minimum\":1}}}"}, 0);

        assertTrue(snapshot.root().isValid(2));
        assertFalse(snapshot.root().isValid(-2));
        assertTrue(snapshot.resolve("defs.json#/$defs/positive").isValid(4));
        assertFalse(snapshot.resolve("defs.json#/$defs/positive").isValid(0));
    }

    @Test
    void selectsAnnotationFragmentWithoutReplacingDocumentRoot() {
        CompiledSchemaSnapshot snapshot = CompiledSchemaSnapshot.create(
                new String[]{"sjf4j:/defs.json"},
                new String[]{"{\"$defs\":{\"text\":{\"type\":\"string\",\"minLength\":4},\"tiny\":{\"type\":\"string\",\"maxLength\":1}}}"}, 0,
                "sjf4j:/defs.json#/$defs/text");

        assertTrue(snapshot.root().isValid("x")); // $defs alone does not assert a constraint
        assertFalse(snapshot.selected().isValid("x"));
        assertTrue(snapshot.selected().isValid("long"));
        assertFalse(snapshot.resolve("#/$defs/tiny").isValid("long"));
    }

    @Test
    void indexesExternalBooleanResourcesForRefs() {
        CompiledSchemaSnapshot snapshot = CompiledSchemaSnapshot.create(
                new String[]{"sjf4j:/root.json", "sjf4j:/false.json", "sjf4j:/true.json"},
                new String[]{"{\"$ref\":\"false.json\"}", "false", "true"}, 0);

        assertFalse(snapshot.root().isValid("anything"));
        assertFalse(snapshot.resolve("false.json").isValid(12));
        assertTrue(snapshot.resolve("true.json").isValid(12));

        CompiledSchemaSnapshot booleanRoot = CompiledSchemaSnapshot.create(
                new String[]{"sjf4j:/false.json"}, new String[]{"false"}, 0);
        assertFalse(booleanRoot.root().isValid("x"));
    }

    @Test
    void rejectsMalformedSnapshot() {
        assertThrows(IllegalArgumentException.class,
                () -> CompiledSchemaSnapshot.create(new String[0], new String[0], 0));
        assertThrows(SchemaException.class,
                () -> CompiledSchemaSnapshot.create(
                        new String[]{"sjf4j:/defs.json"}, new String[]{"{\"$defs\":{\"text\":{\"type\":\"string\",\"minLength\":4},\"tiny\":{\"type\":\"string\",\"maxLength\":1}}}"}, 0,
                        "sjf4j:/defs.json#/$defs/missing"));
    }
}
