package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Directory loading and batch indexing preserve retrieval and canonical schema IDs. */
class SchemaRegistryDirectoryTest {

    @TempDir
    Path registryDirectory;

    @Test
    void loadDirAndIndexResolveCanonicalIds() throws IOException {
        Path nested = Files.createDirectories(registryDirectory.resolve("nested"));
        Path root = registryDirectory.resolve("strict.json");
        Path child = nested.resolve("base.json");
        Files.write(root, ("{\"$id\":\"https://example.test/strict.json\","
                + "\"$ref\":\"base.json\"}").getBytes(StandardCharsets.UTF_8));
        Files.write(child, ("{\"$id\":\"https://example.test/base.json\","
                + "\"type\":\"string\",\"minLength\":3}").getBytes(StandardCharsets.UTF_8));
        Files.write(registryDirectory.resolve("ignored.txt"), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(registryDirectory.resolve("boolean.json"), "false".getBytes(StandardCharsets.UTF_8));

        SchemaRegistry registry = new SchemaRegistry();
        Map<URI, JsonSchema> schemas = new SchemaLoader.Local(registryDirectory.toUri()).loadDir(null);
        assertEquals(3, schemas.size());
        assertTrue(schemas.get(registryDirectory.resolve("boolean.json").toUri()) instanceof BooleanSchema);
        registry.index(schemas);
        assertFalse(registry.resolve(registryDirectory.resolve("boolean.json").toUri()).isValid(null));
        assertTrue(registry.contains(root.toUri()));
        assertTrue(registry.contains(child.toUri()));
        assertTrue(registry.contains(URI.create("https://example.test/base.json")));
        SchemaPlan plan = registry.resolve(URI.create("https://example.test/strict.json"));
        assertNotNull(plan);
        assertTrue(plan.isValid("hello"));
        assertFalse(plan.isValid("no"));
    }

    @Test
    void loadDirCanMapIdlessRemoteSchemasToLogicalBase() throws IOException {
        Path draft = Files.createDirectories(registryDirectory.resolve("draft2020-12"));
        Files.write(draft.resolve("integer.json"),
                "{\"type\":\"integer\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(draft.resolve("alias.json"),
                ("{\"$id\":\"https://schemas.example/alias\","
                        + "\"$ref\":\"http://localhost:1234/draft2020-12/integer.json\"}")
                        .getBytes(StandardCharsets.UTF_8));

        SchemaRegistry registry = new SchemaRegistry();
        URI remote = URI.create("http://localhost:1234/");
        Map<URI, JsonSchema> schemas = new SchemaLoader.Local(registryDirectory.toUri()).loadDir(remote);
        assertEquals(2, schemas.size());
        assertEquals(remote.resolve("draft2020-12/integer.json"),
                ((ObjectSchema) schemas.get(remote.resolve("draft2020-12/integer.json"))).retrievalUri());
        registry.index(schemas);

        assertTrue(registry.contains(remote.resolve("draft2020-12/integer.json")));
        assertTrue(registry.contains(URI.create("https://schemas.example/alias")));
        SchemaPlan integer = registry.resolve(remote.resolve("draft2020-12/integer.json"));
        assertNotNull(integer);
        assertTrue(integer.isValid(1));
        assertFalse(integer.isValid("a"));
        SchemaPlan alias = registry.resolve(URI.create("https://schemas.example/alias"));
        assertTrue(alias.isValid(1));
        assertFalse(alias.isValid("a"));
    }

    @Test
    void batchIndexRejectsConflictsAndIndexIfAbsentPreservesExistingEntries() throws IOException {
        Files.write(registryDirectory.resolve("existing.json"),
                "{\"$id\":\"https://example.test/existing.json\",\"type\":\"number\"}"
                        .getBytes(StandardCharsets.UTF_8));

        SchemaRegistry registry = new SchemaRegistry();
        registry.index(URI.create("https://example.test/existing.json"),
                JsonSchema.fromJson("{\"type\":\"string\"}"));
        Map<URI, JsonSchema> schemas = new SchemaLoader.Local(registryDirectory.toUri()).loadDir(null);
        assertThrows(SchemaException.class, () -> registry.index(schemas));
        schemas.forEach(registry::indexIfAbsent);

        SchemaPlan plan = registry.resolve(URI.create("https://example.test/existing.json"));
        assertTrue(plan.isValid("string"));
        assertFalse(plan.isValid(42));
    }

    @Test
    void loadDirRequiresAnExistingFileDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaLoader.Local(URI.create("classpath:/schemas/")).loadDir(null));
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaLoader.Local(
                        URI.create(registryDirectory.resolve("missing").toUri().toString() + "/")).loadDir(null));
    }
    @Test
    void loadDirRejectsInvalidLogicalBase() {
        SchemaLoader.Local loader = new SchemaLoader.Local(registryDirectory.toUri());
        for (String base : new String[]{"relative/", "https://example.test/file",
                "https://example.test/?query", "https://example.test/#fragment/"}) {
            assertThrows(IllegalArgumentException.class, () -> loader.loadDir(URI.create(base)));
        }
    }

}
