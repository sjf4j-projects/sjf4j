package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.io.StringReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

class SchemaLoaderTest {

    @Test
    void readsBooleanSchemaFromReader() {
        assertSame(BooleanSchema.TRUE, JsonSchema.fromJson(new StringReader("true")));
        assertSame(BooleanSchema.FALSE, JsonSchema.fromJson(new StringReader("false")));
    }

    @Test
    void documentUriPreservesEncodedPathAndQueryWhileRemovingFragments() {
        URI reference = URI.create("https://example.test/schema/a%20b.json?version=2#/$defs/word");
        assertEquals(URI.create("https://example.test/schema/a%20b.json?version=2"),
                SchemaUtil.documentUri(reference));
        assertEquals(URI.create("memory:/schemas/value.json"),
                SchemaUtil.documentUri(URI.create("memory:/schemas/value.json#")));
        URI noFragment = URI.create("classpath:/schemas/value.json");
        assertSame(noFragment, SchemaUtil.documentUri(noFragment));
    }

    @Test
    void resolvesRelativeLocationsAndFragmentsWithoutParsingTheFragmentAsAFile() {
        URI base = URI.create("memory:/schemas/");
        String json = "{\"$defs\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}";
        SchemaLoader loader = uri -> SchemaUtil.documentUri(uri).equals(base.resolve("user.json"))
                ? JsonSchema.fromJson(new StringReader(json)) : null;

        assertEquals(base.resolve("user.json"),
                SchemaUtil.documentUri(base.resolve("user.json#/$defs/name")));
        JsonSchema parsed = loader.load(base.resolve("user.json#/$defs/name"));
        assertTrue(parsed instanceof ObjectSchema);

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
        SchemaLoader loader = uri -> JsonSchema.fromJson(new StringReader("{\"$defs\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}"));
        SchemaRegistry registry = new SchemaRegistry();
        URI reference = base.resolve("user.json#/$defs/name");

        assertNull(registry.resolve(reference));
        JsonSchema schema = loader.load(SchemaUtil.documentUri(reference));
        registry.register(SchemaUtil.documentUri(reference), schema);
        SchemaPlan plan = registry.resolve(reference);
        assertNotNull(plan);
        assertTrue(plan.isValid("ok"));
        assertFalse(plan.isValid("x"));
        assertSame(plan, registry.resolve(reference));
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
            assertEquals(document.toUri(), SchemaUtil.documentUri(local.resolve(selected)));
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
            assertEquals(URI.create(document.toUri().toString() + "#/$defs/name"), loader.resolve(reference));
            JsonSchema schema = loader.load(reference);
            assertTrue(schema instanceof ObjectSchema);
            assertEquals(document.toUri(), SchemaUtil.documentUri(loader.resolve(reference)));
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
            JsonSchema parsed = new SchemaLoader.Local(SchemaLoader.DEFAULT_JSON_SCHEMA_DIR).load(file.toUri());
            assertTrue(parsed instanceof BooleanSchema);
            assertFalse(parsed.formulate().isValid("anything"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void classpathLoaderTreatsSingleAndTripleSlashUrisAsTheSameResource() {
        SchemaLoader.Local loader = new SchemaLoader.Local(URI.create("classpath:/"));
        for (String reference : new String[]{
                "classpath:/schema-loader-classpath-test.json",
                "classpath:///schema-loader-classpath-test.json"}) {
            JsonSchema schema = loader.load(URI.create(reference));
            assertNotNull(schema, reference);
            SchemaPlan plan = schema.formulate();
            assertTrue(plan.isValid("ok"), reference);
            assertFalse(plan.isValid("x"), reference);
        }
    }

    @Test
    void explicitClassLoaderFindsClasspathResourcesOnlyVisibleToChildLoader() throws IOException {
        Path directory = Files.createTempDirectory("sjf4j-child-classpath-");
        String name = "schema-" + directory.getFileName() + ".json";
        Path resource = directory.resolve(name);
        URI uri = URI.create("classpath:/" + name);
        try {
            Files.writeString(resource, "{\"type\":\"integer\"}");
            // The library ClassLoader cannot see dynamically added resources.
            assertNull(new SchemaLoader.Local(URI.create("classpath:/")).load(uri));
            try (URLClassLoader child = new URLClassLoader(
                    new java.net.URL[]{directory.toUri().toURL()},
                    SchemaLoader.class.getClassLoader())) {
                SchemaLoader.Local local = new SchemaLoader.Local(URI.create("classpath:/"), child);
                JsonSchema schema = local.load(uri);
                assertNotNull(schema);
                SchemaRegistry registry = new SchemaRegistry();
                registry.index(uri, schema);
                assertTrue(registry.resolve(uri).isValid(123));
                assertFalse(registry.resolve(uri).isValid("not an integer"));
                assertNull(local.load(URI.create("classpath:/missing-" + name)));
            }
        } finally {
            Files.deleteIfExists(resource);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void customReaderEnablesFutureSchemesWithoutImplicitNetworkAccess() {
        URI base = URI.create("https://example.invalid/schemas/");
        SchemaLoader loader = uri -> {
            assertEquals(base.resolve("value.json"), uri);
            return JsonSchema.fromJson(new StringReader("{\"type\":\"integer\"}"));
        };
        assertTrue(loader.load(base.resolve("value.json")) instanceof ObjectSchema);

        SchemaLoader defaultLoader = new SchemaLoader.Local(SchemaLoader.DEFAULT_JSON_SCHEMA_DIR);
        assertThrows(SchemaException.class, () -> defaultLoader.load(base.resolve("value.json")));
    }

    @Test
    void schemaValidatorUsesInjectedLoaderAndRegistry() {
        URI base = URI.create("memory:/schemas/");
        SchemaLoader loader = new SchemaLoader() {
            @Override
            public URI resolve(URI uri) {
                return base.resolve(uri);
            }

            @Override
            public JsonSchema load(URI uri) {
                assertEquals(base.resolve("sample.json"), uri);
                return JsonSchema.fromJson(new StringReader("{\"type\":\"object\",\"properties\":{\"name\":{\"minLength\":2}}}"));
            }
        };
        SchemaValidator validator = new SchemaValidator(loader, null, true);
        assertTrue(validator.validate(new Sample("ok")).isValid(),
                () -> validator.validate(new Sample("ok")).toString());
        assertFalse(validator.validate(new Sample("x")).isValid());
        assertNotNull(validator.load("sample.json"));
        assertEquals(base.resolve("sample.json"), validator.load("sample.json").schemaUri());
    }

    @ValidJsonSchema(ref = "sample.json")
    public static final class Sample {
        private final String name;
        public Sample() { this(null); }
        public Sample(String name) { this.name = name; }
        public String getName() { return name; }
    }
}
