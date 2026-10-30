package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.io.StringReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLoaderTest {

    @Test
    void resolvesRelativeLocationsAndFragmentsWithoutParsingTheFragmentAsAFile() {
        URI base = URI.create("memory:/schemas/");
        String json = "{\"$defs\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}";
        SchemaLoader loader = uri -> SchemaLoader.documentUri(uri).equals(base.resolve("user.json"))
                ? parseUnchecked(uri, json) : null;

        assertEquals(base.resolve("user.json"),
                SchemaLoader.documentUri(base.resolve("user.json#/$defs/name")));
        JsonSchema parsed = loader.load(base.resolve("user.json#/$defs/name"));
        assertTrue(parsed instanceof ObjectSchema);
        assertEquals(base.resolve("user.json"), ((ObjectSchema) parsed).getRetrievalUri());
        assertEquals(base.resolve("user.json"), ((ObjectSchema) loader.load(base.resolve("user.json"))).getRetrievalUri());

        SchemaRegistry registry = new SchemaRegistry();
        registry.register(base.resolve("user.json"), parsed);
        SchemaPlan fragment = registry.resolve(base.resolve("user.json#/$defs/name"));
        assertTrue(fragment.isValid("Alice"));
        assertFalse(fragment.isValid("A"));
        assertNull(loader.load(base.resolve("missing.json")));
    }

    @Test
    void registryRegistersLoadedSchemaAndResolvesFragment() {
        URI base = URI.create("memory:/schemas/");
        SchemaLoader loader = uri -> parseUnchecked(uri,
                "{\"$defs\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}");
        SchemaRegistry registry = new SchemaRegistry();
        URI reference = base.resolve("user.json#/$defs/name");

        assertNull(registry.resolve(reference));
        JsonSchema schema = loader.load(SchemaLoader.documentUri(reference));
        registry.register(base.resolve("user.json"), schema);
        SchemaPlan plan = registry.resolve(reference);
        assertNotNull(plan);
        assertTrue(plan.isValid("ok"));
        assertFalse(plan.isValid("x"));
        assertSame(plan, SchemaRegistry.copyOf(registry).resolve(reference));
    }

    @Test
    void localLoaderResolvesRelativeUriWithBaseDirectory() throws IOException {
        Path directory = Files.createTempDirectory("sjf4j-schemas-");
        Path document = directory.resolve("user.json");
        try {
            Files.write(document, "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8));
            SchemaLoader.Local local = new SchemaLoader.Local(directory.toUri());
            URI selected = URI.create("user.json#");
            JsonSchema schema = local.load(selected);
            assertTrue(schema instanceof ObjectSchema);
            assertEquals(document.toUri(), ((ObjectSchema) schema).getRetrievalUri());
            assertNull(local.load(URI.create("missing.json")));
            assertEquals(document.toUri(), local.resolve(URI.create("user.json")));
        } finally {
            Files.deleteIfExists(document);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void localLoaderAcceptsWorkingDirectoryRelativeBase() throws IOException {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path directory = Files.createTempDirectory(cwd, "sjf4j-relative-");
        Path document = directory.resolve("user schema.json");
        try {
            Files.write(document, "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8));
            URI relativeBase = URI.create("./" + cwd.relativize(directory).toString() + "/");
            SchemaLoader.Local loader = new SchemaLoader.Local(relativeBase);

            assertEquals(directory.toUri(), loader.baseUri());
            URI reference = URI.create("user%20schema.json#/$defs/name");
            assertEquals(document.toUri().toString() + "#/$defs/name", loader.resolve(reference).toString());
            JsonSchema schema = loader.load(reference);
            assertTrue(schema instanceof ObjectSchema);
            assertEquals(document.toUri(), ((ObjectSchema) schema).getRetrievalUri());
            assertNull(loader.load(URI.create("missing.json")));
        } finally {
            Files.deleteIfExists(document);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void supportsFileResourcesAndBooleanDocuments() throws IOException {
        Path file = Files.createTempFile("sjf4j-loader-", ".json");
        try {
            Files.write(file, "false".getBytes(StandardCharsets.UTF_8));
            JsonSchema parsed = new SchemaLoader.Local(SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR).load(file.toUri());
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

        SchemaLoader defaultLoader = new SchemaLoader.Local(SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR);
        assertThrows(SchemaException.class, () -> defaultLoader.load(base.resolve("value.json")));
    }

    @Test
    void schemaValidatorUsesInjectedLoaderAndRegistry() {
        URI base = URI.create("memory:/schemas/");
        SchemaLoader loader = uri -> parseUnchecked(base.resolve(uri),
                "{\"type\":\"object\",\"properties\":{\"name\":{\"minLength\":2}}}");
        SchemaValidator validator = new SchemaValidator(loader, null, true);
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
