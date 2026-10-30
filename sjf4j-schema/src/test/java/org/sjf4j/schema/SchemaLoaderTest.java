package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.io.ByteArrayInputStream;
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
        SchemaLoader loader = new SchemaLoader(base, uri ->
                uri.equals(base.resolve("user.json"))
                        ? new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
                        : null);

        assertEquals(base.resolve("user.json#/$defs/name"),
                loader.resolve("user.json#/$defs/name"));
        JsonSchema parsed = loader.load("user.json#/$defs/name");
        assertTrue(parsed instanceof ObjectSchema);
        assertEquals(base.resolve("user.json"), ((ObjectSchema) parsed).getRetrievalUri());
        assertEquals(json, loader.readText(loader.resolve("user.json")));

        SchemaRegistry registry = new SchemaRegistry();
        registry.register(base.resolve("user.json"), parsed);
        SchemaPlan fragment = registry.resolve(loader.resolve("user.json#/$defs/name"));
        assertTrue(fragment.isValid("Alice"));
        assertFalse(fragment.isValid("A"));
        assertNull(loader.load("missing.json"));
    }

    @Test
    void supportsFileResourcesAndBooleanDocuments() throws IOException {
        Path file = Files.createTempFile("sjf4j-loader-", ".json");
        try {
            Files.write(file, "false".getBytes(StandardCharsets.UTF_8));
            JsonSchema parsed = new SchemaLoader().load(file.toUri());
            assertTrue(parsed instanceof BooleanSchema);
            assertFalse(parsed.createPlan().isValid("anything"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void customReaderEnablesFutureSchemesWithoutImplicitNetworkAccess() {
        URI base = URI.create("https://example.invalid/schemas/");
        SchemaLoader loader = new SchemaLoader(base, uri -> {
            assertEquals(base.resolve("value.json"), uri);
            return new ByteArrayInputStream("{\"type\":\"integer\"}".getBytes(StandardCharsets.UTF_8));
        });
        assertTrue(loader.load("value.json") instanceof ObjectSchema);

        SchemaLoader defaultLoader = new SchemaLoader(base);
        assertThrows(SchemaException.class, () -> defaultLoader.load("value.json"));
    }

    @Test
    void schemaValidatorUsesInjectedLoaderAndRegistry() {
        URI base = URI.create("memory:/schemas/");
        SchemaLoader loader = new SchemaLoader(base, uri ->
                new ByteArrayInputStream(
                        "{\"type\":\"object\",\"properties\":{\"name\":{\"minLength\":2}}}"
                                .getBytes(StandardCharsets.UTF_8)));
        SchemaValidator validator = SchemaValidator.withLoader(loader, null, true);
        assertTrue(validator.validate(new Sample("ok")).isValid());
        assertFalse(validator.validate(new Sample("x")).isValid());
        assertNotNull(validator.load("sample.json"));
    }

    @ValidJsonSchema(ref = "sample.json")
    public static final class Sample {
        private final String name;
        public Sample(String name) { this.name = name; }
        public String getName() { return name; }
    }
}
