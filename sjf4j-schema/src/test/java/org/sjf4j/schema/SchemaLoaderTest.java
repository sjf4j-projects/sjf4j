package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.io.StringReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLoaderTest {

    @Test
    void resolvesRelativeLocationsAndFragmentsWithoutParsingTheFragmentAsAFile() {
        URI base = URI.create("memory:/schemas/");
        String json = "{\"$defs\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}";
        SchemaLoader loader = uri -> uri.equals(base.resolve("user.json"))
                ? parseUnchecked(uri, json) : null;

        assertEquals(base.resolve("user.json#/$defs/name"),
                base.resolve("user.json#/$defs/name"));
        JsonSchema parsed = loader.load(base.resolve("user.json#/$defs/name"));
        assertTrue(parsed instanceof ObjectSchema);
        assertEquals(base.resolve("user.json"), ((ObjectSchema) parsed).getRetrievalUri());
        assertEquals(base.resolve("user.json"), ((ObjectSchema) loader.load(base.resolve("user.json"))).getRetrievalUri());

        SchemaRegistry registry = new SchemaRegistry();
        registry.register(base.resolve("user.json"), parsed);
        SchemaPlan fragment = registry.resolve(loader.resolve("user.json#/$defs/name"));
        assertTrue(fragment.isValid("Alice"));
        assertFalse(fragment.isValid("A"));
        assertNull(loader.load(base.resolve("missing.json")));
    }

    @Test
    void supportsFileResourcesAndBooleanDocuments() throws IOException {
        Path file = Files.createTempFile("sjf4j-loader-", ".json");
        try {
            Files.write(file, "false".getBytes(StandardCharsets.UTF_8));
            JsonSchema parsed = SchemaLoader.LOCAL.load(file.toUri());
            assertTrue(parsed instanceof BooleanSchema);
            assertFalse(parsed.createPlan().isValid("anything"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void customReaderEnablesFutureSchemesWithoutImplicitNetworkAccess() {
        URI base = URI.create("https://example.invalid/schemas/");
        SchemaLoader loader = uri -> {
            assertEquals(base.resolve("value.json"), uri);
            return parseUnchecked(uri, "{\"type\":\"integer\"}");
        };
        assertTrue(loader.load(base.resolve("value.json")) instanceof ObjectSchema);

        SchemaLoader defaultLoader = SchemaLoader.LOCAL;
        assertThrows(SchemaException.class, () -> defaultLoader.load(base.resolve("value.json")));
    }

    @Test
    void schemaValidatorUsesInjectedLoaderAndRegistry() {
        URI base = URI.create("memory:/schemas/");
        SchemaLoader loader = uri -> parseUnchecked(uri,
                "{\"type\":\"object\",\"properties\":{\"name\":{\"minLength\":2}}}");
        SchemaValidator validator = SchemaValidator.withLoader(loader, null, true);
        assertTrue(validator.validate(new Sample("ok")).isValid(),
                () -> validator.validate(new Sample("ok")).toString());
        assertFalse(validator.validate(new Sample("x")).isValid());
        assertNotNull(validator.load("sample.json"));
    }

    private static JsonSchema parseUnchecked(URI uri, String text) {
        try {
            return SchemaLoader.parse(uri, new StringReader(text));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @ValidJsonSchema(ref = "sample.json")
    public static final class Sample {
        private final String name;
        public Sample() { this(null); }
        public Sample(String name) { this.name = name; }
        public String getName() { return name; }
    }
}
