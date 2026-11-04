package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaDynamicRefResolutionTest {
    @Test
    void resolvesUsingOutermostScopeAndRetainsInitialTargetWhenItWins() {
        SchemaRegistry registry = new SchemaRegistry();
        URI genericUri = URI.create("https://example.test/dynamic/generic");
        URI numbersUri = URI.create("https://example.test/dynamic/numbers");
        URI stringsUri = URI.create("https://example.test/dynamic/strings");
        registry.index(genericUri, JsonSchema.fromJson(
                "{\"$defs\":{\"default\":{\"$dynamicAnchor\":\"item\",\"type\":\"null\"}},"
                        + "\"$dynamicRef\":\"#item\"}"));
        registry.index(numbersUri, JsonSchema.fromJson(
                "{\"$dynamicAnchor\":\"item\",\"type\":\"number\"}"));
        registry.index(stringsUri, JsonSchema.fromJson(
                "{\"$dynamicAnchor\":\"item\",\"type\":\"string\"}"));

        SchemaPlan generic = registry.resolve(genericUri);
        SchemaPlan numbers = registry.resolve(numbersUri);
        SchemaPlan strings = registry.resolve(stringsUri);
        URI ref = URI.create(genericUri + "#item");
        SchemaPlan initial = registry.resolve(ref);

        assertEquals(initial, registry.resolveDynamicRef(ref, Collections.emptyList()));
        assertEquals(numbers, registry.resolveDynamicRef(ref, Arrays.asList(numbers, generic)));
        assertEquals(strings, registry.resolveDynamicRef(ref, Arrays.asList(strings, generic)));
        // The first matching anchor wins even if it equals the initial target.
        assertEquals(initial, registry.resolveDynamicRef(ref, Arrays.asList(generic, numbers)));
        assertEquals(numbers, registry.resolveDynamicRef(ref, Arrays.asList(numbers, strings, generic)));
    }

    @Test
    void regularAnchorAndPointerIgnoreDynamicScope() {
        SchemaRegistry registry = new SchemaRegistry();
        URI base = URI.create("https://example.test/dynamic/plain");
        URI other = URI.create("https://example.test/dynamic/other");
        registry.index(base, JsonSchema.fromJson(
                "{\"$defs\":{\"target\":{\"$anchor\":\"foo\","
                        + "\"$dynamicAnchor\":\"bar\",\"type\":\"string\"}}}"));
        registry.index(other, JsonSchema.fromJson(
                "{\"$dynamicAnchor\":\"bar\",\"type\":\"integer\"}"));
        SchemaPlan override = registry.resolve(other);
        SchemaPlan anchor = registry.resolve(URI.create(base + "#foo"));
        SchemaPlan pointer = registry.resolve(URI.create(base + "#/$defs/target"));

        assertEquals(anchor, registry.resolveDynamicRef(URI.create(base + "#foo"),
                Collections.singletonList(override)));
        assertEquals(pointer, registry.resolveDynamicRef(URI.create(base + "#/$defs/target"),
                Collections.singletonList(override)));
        assertThrows(SchemaException.class, () -> registry.resolveDynamicRef(
                URI.create(base + "#missing"), Collections.emptyList()));
    }
}
