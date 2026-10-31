package org.sjf4j.processor.schema;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SchemaDiagnosticMetadataTest {

    @Test
    void keywordDiagnosticSeparatesResourceUriAndNestedPointer() {
        SchemaCheck.Diagnostic diagnostic = SchemaCompiler.diagnostic(
                "classpath:/schemas/model.json#/$defs/person/properties/a~1b",
                "minimum");

        assertNotNull(diagnostic);
        assertEquals("minimum", diagnostic.keyword);
        assertEquals("classpath:/schemas/model.json", diagnostic.schemaUri);
        assertEquals("/$defs/person/properties/a~1b/minimum", diagnostic.keywordPointer);

        SchemaCheck.TypeCheck check = new SchemaCheck.TypeCheck(
                Collections.singletonList("integer"), diagnostic);
        assertSame(diagnostic, check.diagnostic);
    }

    @Test
    void keywordDiagnosticDecodesUriFragmentBeforeBuildingPointer() {
        SchemaCheck.Diagnostic diagnostic = SchemaCompiler.diagnostic(
                "classpath:/schema%20draft.json#/$defs/name%20with%20space",
                "minLength");

        assertEquals("classpath:/schema%20draft.json", diagnostic.schemaUri);
        assertEquals("/$defs/name with space/minLength", diagnostic.keywordPointer);
    }

    @Test
    void booleanFalseDiagnosticPointsAtSchemaLocationWithoutInventingKeywordToken() {
        SchemaCheck.Diagnostic diagnostic = SchemaCompiler.schemaDiagnostic(
                "classpath:/schemas/model.json#/properties/child",
                "false");

        assertEquals("false", diagnostic.keyword);
        assertEquals("classpath:/schemas/model.json", diagnostic.schemaUri);
        assertEquals("/properties/child", diagnostic.keywordPointer);
    }

    @Test
    void nestedIdStartsANewDiagnosticResourceAndResetsItsPointer() {
        String effective = SchemaCompiler.effectiveSchemaUri(
                "classpath:/schemas/root.json#/properties/child",
                org.sjf4j.JsonObject.of("$id", "child.json"));

        assertEquals("classpath:/schemas/child.json", effective);
        SchemaCheck.Diagnostic diagnostic = SchemaCompiler.diagnostic(effective, "minimum");
        assertEquals("classpath:/schemas/child.json", diagnostic.schemaUri);
        assertEquals("/minimum", diagnostic.keywordPointer);
    }

    @Test
    void unchangedIdKeepsTheCurrentNestedPointer() {
        String effective = SchemaCompiler.effectiveSchemaUri(
                "classpath:/schemas/root.json#/properties/child",
                org.sjf4j.JsonObject.of("$id", "root.json"));

        assertEquals("classpath:/schemas/root.json#/properties/child", effective);
    }

    @Test
    void unsupportedAnchorLocationDoesNotProduceAnInvalidJsonPointer() {
        assertNull(SchemaCompiler.diagnostic("classpath:/schemas/model.json#person", "type"));
    }
}
