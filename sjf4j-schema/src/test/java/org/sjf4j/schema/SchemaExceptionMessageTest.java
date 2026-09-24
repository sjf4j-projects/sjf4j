package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.exception.NodeException;

import java.net.URI;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaExceptionMessageTest {
    private static final String SCHEMA_URI = "https://example.com/messages.json";

    @ParameterizedTest
    @MethodSource("negativeKeywords")
    void negativeKeyword_reportsSchemaLocation(String keyword) {
        SchemaException error = assertThrows(SchemaException.class,
                () -> schema("{\"properties\":{\"nested\":{\"" + keyword + "\":-1}}}").createPlan());

        assertEquals(SchemaException.class, error.getClass());
        assertSchemaMessage(error, "schema.invalid", "invalid '" + keyword + "' keyword: value must be >= 0",
                "/properties/nested/" + keyword, SCHEMA_URI);
    }

    private static Stream<Arguments> negativeKeywords() {
        return Stream.of(
                Arguments.of("minLength"),
                Arguments.of("maxLength"),
                Arguments.of("minProperties"),
                Arguments.of("maxProperties"),
                Arguments.of("minItems"),
                Arguments.of("maxItems"),
                Arguments.of("minContains"),
                Arguments.of("maxContains"));
    }

    @Test
    void invalidType_reportsSchemaLocation() {
        SchemaException error = assertThrows(SchemaException.class,
                () -> schema("{\"type\":1}").createPlan());

        assertEquals(SchemaException.class, error.getClass());
        assertSchemaMessage(error, "schema.invalid", "invalid 'type' keyword", "/type", SCHEMA_URI);
    }

    @Test
    void invalidMultipleOf_reportsSchemaLocation() {
        SchemaException error = assertThrows(SchemaException.class,
                () -> schema("{\"multipleOf\":0}").createPlan());

        assertEquals(SchemaException.class, error.getClass());
        assertSchemaMessage(error, "schema.invalid", "invalid 'multipleOf' keyword", "/multipleOf", SCHEMA_URI);
    }

    @Test
    void invalidPattern_reportsSchemaLocation() {
        SchemaException error = assertThrows(SchemaException.class,
                () -> schema("{\"pattern\":\"[\"}").createPlan());

        assertEquals(SchemaException.class, error.getClass());
        assertSchemaMessage(error, "schema.invalid", "invalid regex for keyword 'pattern'", "/pattern", SCHEMA_URI);
    }

    @Test
    void invalidPatternProperties_reportsSchemaLocation() {
        SchemaException error = assertThrows(SchemaException.class,
                () -> schema("{\"patternProperties\":{\"[\":{\"type\":\"string\"}}}").createPlan());

        assertEquals(SchemaException.class, error.getClass());
        assertSchemaMessage(error, "schema.invalid", "invalid regex for keyword 'patternProperties'",
                "/patternProperties/[", SCHEMA_URI);
    }

    @Test
    void registryMissingRootUri_reportsInlineRootLocation() {
        SchemaException error = assertThrows(SchemaException.class,
                () -> new SchemaRegistry().index(JsonSchema.fromJson("{}")));

        assertEquals(SchemaException.class, error.getClass());
        assertSchemaMessage(error, "schema.uri", "missing root schema uri", "/", "<inline>");
    }

    @Test
    void unsupportedLocalLoad_reportsRootLocation() {
        URI uri = URI.create("https://example.com/schema.json");
        SchemaException error = assertThrows(SchemaException.class,
                () -> SchemaUtil.loadSchemaFromLocalUri(uri));

        assertEquals(SchemaException.class, error.getClass());
        assertSchemaMessage(error, "schema.load", "unsupported local schema uri", "/", uri.toString());
    }

    @ParameterizedTest
    @MethodSource("referenceKeywords")
    void nestedReference_reportsDeclaringResource(String keyword) {
        String childUri = "https://example.com/child.json";
        SchemaException error = assertThrows(SchemaException.class,
                () -> schema("{\"properties\":{\"nested\":{\"$id\":\"" + childUri + "\",\""
                        + keyword + "\":\"missing\"}}}").createPlan());

        assertEquals(SchemaException.class, error.getClass());
        assertSchemaMessage(error, "schema.resolve", "cannot resolve schema resource", "/" + keyword, childUri);
        assertFalse(error.getMessage().contains("schema=" + SCHEMA_URI));
    }

    @ParameterizedTest
    @MethodSource("referenceKeywords")
    void opaqueReference_reportsDeclaringKeywordAndPreservesCause(String keyword) {
        String childUri = "urn:example:opaque";
        SchemaException error = assertThrows(SchemaException.class,
                () -> schema("{\"properties\":{\"nested\":{\"$id\":\"" + childUri + "\",\""
                        + keyword + "\":\"other\"}}}").createPlan());

        assertEquals(SchemaException.class, error.getClass());
        assertEquals("SCHEMA schema.resolve: cannot resolve " + keyword + " URI 'other': base='"
                        + childUri + "', ref='other' (keyword=/" + keyword + ", schema=" + childUri + ")",
                error.getMessage());

        assertTrue(error.getCause() instanceof SchemaException);
        SchemaException cause = (SchemaException) error.getCause();
        assertEquals("SCHEMA schema.resolve: cannot resolve relative uri against opaque base uri: base='"
                        + childUri + "', ref='other' (keyword=/, schema=" + childUri + ")",
                cause.getMessage());
    }

    @Test
    void registryOpaqueReference_reportsRootLocation() {
        URI baseUri = URI.create("urn:example:opaque");
        SchemaException error = assertThrows(SchemaException.class,
                () -> new SchemaRegistry().index(baseUri, JsonSchema.fromJson("{\"$id\":\"other\"}")));

        assertEquals("SCHEMA schema.resolve: cannot resolve relative uri against opaque base uri: base='"
                        + baseUri + "', ref='other' (keyword=/, schema=" + baseUri + ")",
                error.getMessage());
    }

    private static Stream<Arguments> referenceKeywords() {
        return Stream.of(Arguments.of("$ref"), Arguments.of("$dynamicRef"));
    }

    @Test
    void runtimeCycle_isNodeExceptionWithSchemaLocation() {
        SchemaPlan plan = schema("{\"$ref\":\"#\"}").createPlan();

        NodeException error = assertThrows(NodeException.class, () -> plan.validate("value"));
        assertEquals(NodeException.class, error.getClass());
        assertSchemaMessage(error, "schema.resolve", "cyclic schema reference detected", "/$ref", SCHEMA_URI);
    }

    private static ObjectSchema schema(String body) {
        return schema(SCHEMA_URI, body);
    }

    private static ObjectSchema schema(String schemaUri, String body) {
        String fields = body.length() == 2 ? "" : "," + body.substring(1, body.length() - 1);
        return (ObjectSchema) JsonSchema.fromJson("{\"$id\":\"" + schemaUri + "\"" + fields + "}");
    }

    private static void assertSchemaMessage(RuntimeException error, String code, String summary,
                                            String keyword, String schemaUri) {
        String message = error.getMessage();
        assertTrue(message.startsWith("SCHEMA " + code + ":"));
        assertTrue(message.contains(summary));
        assertTrue(message.contains("keyword=" + keyword));
        assertTrue(message.contains("schema=" + schemaUri));
    }
}
