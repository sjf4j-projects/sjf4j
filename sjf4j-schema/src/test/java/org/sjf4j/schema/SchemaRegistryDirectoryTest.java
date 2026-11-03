package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** A registry can preload complete directories while preserving canonical schema IDs. */
class SchemaRegistryDirectoryTest {

    @TempDir
    Path registryDirectory;

    @Test
    void indexDirRegistersNestedResourcesAndResolvesCanonicalIds() throws IOException {
        Path nested = Files.createDirectories(registryDirectory.resolve("nested"));
        Path root = registryDirectory.resolve("strict.json");
        Path child = nested.resolve("base.json");
        Files.write(root, ("{\"$id\":\"https://example.test/strict.json\","
                + "\"$ref\":\"base.json\"}").getBytes(StandardCharsets.UTF_8));
        Files.write(child, ("{\"$id\":\"https://example.test/base.json\","
                + "\"type\":\"string\",\"minLength\":3}").getBytes(StandardCharsets.UTF_8));
        Files.write(registryDirectory.resolve("ignored.txt"), "{}".getBytes(StandardCharsets.UTF_8));

        SchemaRegistry registry = new SchemaRegistry();
        registry.indexDir(registryDirectory.toUri());
        assertTrue(registry.contains(root.toUri()));
        assertTrue(registry.contains(child.toUri()));
        assertTrue(registry.contains(URI.create("https://example.test/base.json")));
        SchemaPlan plan = registry.resolve(URI.create("https://example.test/strict.json"));
        assertNotNull(plan);
        assertTrue(plan.isValid("hello"));
        assertFalse(plan.isValid("no"));
    }

    @Test
    void indexDirCanRegisterIdlessRemoteSchemasWithRetrievalBase() throws IOException {
        Path draft = Files.createDirectories(registryDirectory.resolve("draft2020-12"));
        Files.write(draft.resolve("integer.json"),
                "{\"type\":\"integer\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(draft.resolve("alias.json"),
                ("{\"$id\":\"https://schemas.example/alias\","
                        + "\"$ref\":\"integer.json\"}").getBytes(StandardCharsets.UTF_8));

        SchemaRegistry registry = new SchemaRegistry();
        URI remote = URI.create("http://localhost:1234/");
        registry.indexDir(registryDirectory.toUri(), remote);

        assertTrue(registry.contains(remote.resolve("draft2020-12/integer.json")));
        assertTrue(registry.contains(URI.create("https://schemas.example/alias")));
        SchemaPlan integer = registry.resolve(remote.resolve("draft2020-12/integer.json"));
        assertNotNull(integer);
        assertTrue(integer.isValid(1));
        assertFalse(integer.isValid("a"));
    }

    @Test
    void indexDirPreservesExistingRegistryEntries() throws IOException {
        Files.write(registryDirectory.resolve("existing.json"),
                "{\"$id\":\"https://example.test/existing.json\",\"type\":\"number\"}"
                        .getBytes(StandardCharsets.UTF_8));

        SchemaRegistry registry = new SchemaRegistry();
        registry.index(URI.create("https://example.test/existing.json"),
                JsonSchema.fromJson("{\"type\":\"string\"}"));
        registry.indexDir(registryDirectory.toUri());

        SchemaPlan plan = registry.resolve(URI.create("https://example.test/existing.json"));
        assertTrue(plan.isValid("string"));
        assertFalse(plan.isValid(42));
    }

    @Test
    void indexDirRequiresAnExistingFileDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaRegistry().indexDir(URI.create("classpath:/schemas/")));
        assertThrows(IllegalArgumentException.class,
                () -> new SchemaRegistry().indexDir(
                        URI.create(registryDirectory.resolve("missing").toUri().toString() + "/")));
    }
}
