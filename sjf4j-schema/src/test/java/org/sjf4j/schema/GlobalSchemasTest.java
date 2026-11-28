package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class GlobalSchemasTest {

    @Test
    void sharedResourcesCompileInEachRegistryWithLocalReferences() {
        URI canonical = URI.create("https://example.test/global-schemas/root.json");
        URI alias = canonical.resolve("alias.json");
        URI leaf = canonical.resolve("leaf.json");
        JsonSchema schema = JsonSchema.fromJson("{\"$id\":\"root.json\",\"$ref\":\"leaf.json\"}");
        GlobalSchemas.index(alias, schema);
        GlobalSchemas.index(alias, schema);

        SchemaRegistry first = new SchemaRegistry();
        SchemaRegistry second = new SchemaRegistry();
        first.index(leaf, JsonSchema.fromJson("{\"type\":\"string\"}"));
        second.index(leaf, JsonSchema.fromJson("{\"type\":\"integer\"}"));

        assertTrue(first.contains(canonical));
        assertTrue(second.contains(alias));
        assertNull(first.resolveBuilt(canonical));
        assertNull(second.resolveBuilt(canonical));
        SchemaPlan a = first.resolve(alias);
        SchemaPlan b = second.resolve(canonical);
        assertSame(a, first.resolve(canonical));
        assertSame(b, second.resolve(alias));
        assertNotSame(a, b);
        assertTrue(a.isValid("value"));
        assertFalse(a.isValid(1));
        assertTrue(b.isValid(1));
        assertFalse(b.isValid("value"));
    }

    @Test
    void localResourcesTakePrecedenceOverGlobals() {
        URI uri = URI.create("https://example.test/global-schemas/shadow");
        GlobalSchemas.index(JsonSchema.fromJson("{\"$id\":\"" + uri + "\",\"type\":\"string\"}"));

        SchemaRegistry local = new SchemaRegistry();
        local.index(uri, JsonSchema.fromJson("{\"type\":\"integer\"}"));
        assertTrue(local.resolve(uri).isValid(1));
        assertFalse(local.resolve(uri).isValid("value"));

        SchemaRegistry other = new SchemaRegistry();
        assertTrue(other.resolve(uri).isValid("value"));
        assertFalse(other.resolve(uri).isValid(1));
    }

    @Test
    void booleanResourcesHaveNoSharedPlanCache() {
        URI uri = URI.create("https://example.test/global-schemas/boolean");
        GlobalSchemas.index(uri, BooleanSchema.FALSE);
        GlobalSchemas.index(uri, BooleanSchema.FALSE);

        SchemaRegistry first = new SchemaRegistry();
        SchemaRegistry second = new SchemaRegistry();
        assertNull(first.resolveBuilt(uri));
        assertNull(second.resolveBuilt(uri));
        SchemaPlan a = first.resolve(uri);
        assertNotNull(a);
        assertFalse(a.isValid("value"));
        assertSame(a, first.resolve(uri));
        SchemaPlan b = second.resolve(uri);
        assertNotNull(b);
        assertNotSame(a, b);
        assertFalse(b.isValid(null));

        JsonSchema ref = JsonSchema.fromJson("{\"$ref\":\"" + uri + "\"}");
        assertFalse(ref.formulate().isValid(1));
        SchemaRegistry local = new SchemaRegistry();
        local.index(uri, BooleanSchema.TRUE);
        assertTrue(local.resolve(uri).isValid(1));
        assertThrows(SchemaException.class, () -> GlobalSchemas.index(uri, BooleanSchema.TRUE));
    }

    @Test
    void conflictingAliasesDoNotPublishNewResources() {
        URI alias = URI.create("https://example.test/global-schemas/conflict-alias");
        URI canonical = alias.resolve("conflict-canonical");
        JsonSchema original = JsonSchema.fromJson("{\"type\":\"string\"}");
        GlobalSchemas.index(alias, original);
        JsonSchema conflict = JsonSchema.fromJson("{\"$id\":\"" + canonical + "\",\"type\":\"integer\"}");

        assertThrows(SchemaException.class, () -> GlobalSchemas.index(alias, conflict));
        assertNull(GlobalSchemas.find(canonical.toString()));
        assertSame(original, GlobalSchemas.find(alias.toString()).schema);
        assertThrows(SchemaException.class, () -> GlobalSchemas.index(JsonSchema.fromJson("{}")));
        assertThrows(SchemaException.class, () -> GlobalSchemas.index(BooleanSchema.TRUE));
        assertThrows(SchemaException.class,
                () -> GlobalSchemas.index(URI.create("relative.json"), original));
    }

    @Test
    void draft07CanonicalAndHttpsAliasRemainAvailable() {
        SchemaRegistry registry = new SchemaRegistry();
        SchemaPlan canonical = registry.resolve(URI.create("http://json-schema.org/draft-07/schema"));
        assertNotNull(canonical);
        assertSame(canonical, registry.resolve(URI.create("https://json-schema.org/draft-07/schema")));
    }
}
