package org.sjf4j.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.schema.JsonSchema;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class SchemaSupportTest {
    private static final URI RETRIEVAL = URI.create("classpath:/schemas/root.json");

    @Test
    void traversesOnlySchemaPositionsAndResolvesNestedIds() {
        JsonSchema schema = JsonSchema.fromJson(
                "{\"$id\":\"https://example.test/root\","
                + "\"$defs\":{\"nested\":{\"$id\":\"nested/part\","
                + "\"$ref\":\"../other#word\","
                + "\"properties\":{\"x\":{\"$ref\":\"#/$defs/child\"}}}},"
                + "\"const\":{\"$schema\":\"http://json-schema.org/draft-07/schema#\","
                + "\"$ref\":\"https://ignored.test/\"}}");
        SchemaSupport.Analysis analyzed = SchemaSupport.analyze(RETRIEVAL, schema);
        assertTrue(analyzed.referencedDocuments.contains(URI.create("https://example.test/other")));
        assertTrue(analyzed.referencedDocuments.contains(URI.create("https://example.test/nested/part")));
        assertEquals(2, analyzed.referencedDocuments.size());
        assertFalse(analyzed.customDialect);
    }

    @Test
    void rejectsExplicitOlderDraftAtAnySchemaPosition() {
        JsonSchema old = JsonSchema.fromJson(
                "{\"$defs\":{\"child\":{\"$schema\":\"https://json-schema.org/draft/2019-09/schema\"}}}");
        assertThrows(IllegalArgumentException.class, () -> SchemaSupport.analyze(RETRIEVAL, old));
    }

    @Test
    void unknownDialectRequiresFallbackButAbsentDialectDefaultsTo202012() {
        assertTrue(SchemaSupport.analyze(RETRIEVAL,
                JsonSchema.fromJson("{\"$defs\":{\"child\":{\"$schema\":\"https://example.test/custom\"}}}"))
                .customDialect);
        assertFalse(SchemaSupport.analyze(RETRIEVAL,
                JsonSchema.fromJson("{\"type\":\"string\"}")).customDialect);
        assertFalse(SchemaSupport.analyze(RETRIEVAL,
                JsonSchema.fromJson("false")).customDialect);
    }
}
