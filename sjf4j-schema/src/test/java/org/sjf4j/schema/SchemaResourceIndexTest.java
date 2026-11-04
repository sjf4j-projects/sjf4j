package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaResourceIndexTest {

    @Test
    void resolvesCanonicalAliasesAndAnchors() {
        Map<URI, String> documents = new LinkedHashMap<>();
        documents.put(URI.create("classpath:/schemas/entry.json"), "{\"$id\":\"https://example.test/root.json\", \"$defs\":{\"word\":{\"$anchor\":\"word\",\"type\":\"string\"}, \"nested\":{\"$id\":\"nested.json\", \"$defs\":{\"number\":{\"$anchor\":\"number\",\"type\":\"number\"}}}}}");
        SchemaResourceIndex index = new SchemaResourceIndex(documents, true);
        SchemaResourceIndex.Target anchor =
                index.resolve("https://example.test/root.json", "#word");
        assertNotNull(anchor);
        assertEquals("https://example.test/root.json#/$defs/word", anchor.uri);
        assertSame(anchor.node,
                index.get(URI.create("classpath:/schemas/entry.json#/$defs/word")).node);
        SchemaResourceIndex.Target nested =
                index.resolve("https://example.test/root.json", "nested.json#number");
        assertNotNull(nested);
        assertEquals("https://example.test/nested.json#/$defs/number", nested.uri);
    }

    @Test
    void doesNotTreatInstanceDataAsSchemaAnchors() {
        Map<URI, String> documents = java.util.Collections.singletonMap(URI.create("https://example.test/root"), "{\"const\":{\"$anchor\":\"incorrect\",\"type\":\"string\"}, \"$defs\":{\"actual\":{\"$anchor\":\"correct\",\"type\":\"string\"}}}");
        SchemaResourceIndex index = new SchemaResourceIndex(documents, true);
        assertNull(index.resolve("https://example.test/root", "#incorrect"));
        assertNotNull(index.resolve("https://example.test/root", "#correct"));
    }

    @Test
    void explicitOlderDialectsOnlyRejectedForCompiledMode() {
        Map<URI, String> documents = java.util.Collections.singletonMap(URI.create("https://example.test/root"), "{\"$defs\":{\"nested\":{\"$schema\":\"https://json-schema.org/draft/2019-09/schema\", \"type\":\"string\"}}}");
        assertDoesNotThrow(() -> new SchemaResourceIndex(documents));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new SchemaResourceIndex(documents, true));
        assertTrue(failure.getMessage().contains("Draft 2020-12"));
    }

    @Test
    void runtimeRegistryCanResolveRawNodesWithoutBuildingPlans() {
        SchemaRegistry registry = new SchemaRegistry();
        URI retrieval = URI.create("https://example.test/retrieved.json");
        registry.index(retrieval, JsonSchema.fromJson("{\"$id\":\"https://example.test/canonical.json\", \"$defs\":{\"word\":{\"$anchor\":\"word\",\"type\":\"string\"}}}"));
        SchemaResourceIndex.Target target = registry.resourceIndex()
                .get(URI.create("https://example.test/canonical.json#word"));
        assertNotNull(target);
        assertEquals("https://example.test/canonical.json#/$defs/word", target.uri);
        assertNotNull(registry.resourceIndex()
                .get(URI.create("https://example.test/retrieved.json#/$defs/word")));
    }
}
