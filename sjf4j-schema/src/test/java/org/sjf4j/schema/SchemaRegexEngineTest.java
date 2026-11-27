package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.regex.JdkRegexEngine;
import org.sjf4j.regex.Regex;
import org.sjf4j.regex.RegexEngine;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaRegexEngineTest {

    @Test
    void customEngineIsUsedForPatternsAndPatternProperties() {
        List<String> expressions = new ArrayList<>();
        RegexEngine engine = (expression, flags) -> {
            expressions.add(expression);
            assertEquals(0, flags);
            return new Regex() {
                @Override
                public boolean matches(String input) {
                    throw new AssertionError("Schema patterns must use find()");
                }

                @Override
                public boolean find(String input) {
                    return input.contains("custom");
                }
            };
        };
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_2020_12, engine);
        SchemaPlan pattern = JsonSchema.fromJson("{\"pattern\":\"abc\"}").createPlan(registry);
        assertTrue(pattern.isValid("custom"));
        assertFalse(pattern.isValid("abc"));

        SchemaPlan properties = JsonSchema.fromJson(
                "{\"patternProperties\":{\"abc\":true},\"additionalProperties\":false}")
                .createPlan(registry);
        assertTrue(properties.isValid(Collections.singletonMap("custom-key", 1)));
        assertFalse(properties.isValid(Collections.singletonMap("abc", 1)));
        assertEquals(2, expressions.size());
        assertEquals("abc", expressions.get(0));
        assertEquals("abc", expressions.get(1));

        // Matching reuses compiled expressions, and the default engine is unaffected.
        assertTrue(pattern.isValid("custom again"));
        assertEquals(2, expressions.size());
        assertTrue(JsonSchema.fromJson("{\"pattern\":\"abc\"}").createPlan().isValid("abc"));
    }

    @Test
    void copiesAndLazyReferencesPreserveEngine() {
        IllegalArgumentException rejection = new IllegalArgumentException("blocked by policy");
        RegexEngine guarded = new JdkRegexEngine().guardedBy((expression, flags) -> {
            if ("blocked".equals(expression)) throw rejection;
        });
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_2020_12, guarded);
        URI target = URI.create("https://example.test/regex-target");
        registry.index(target, JsonSchema.fromJson("{\"pattern\":\"blocked\"}"));
        SchemaRegistry copy = SchemaRegistry.copyOf(registry);
        assertSame(guarded, copy.getRegexEngine());

        SchemaException error = assertThrows(SchemaException.class,
                () -> JsonSchema.fromJson("{\"$ref\":\"https://example.test/regex-target\"}")
                        .createPlan(copy));
        assertSame(rejection, error.getCause());
        assertThrows(SchemaException.class, () -> registry.resolve(target));
    }

    @Test
    void builtInResourcesAreNotReusedAcrossEngines() {
        URI uri = URI.create("https://json-schema.org/draft-07/schema");
        SchemaPlan global = SchemaRegistry.globalResolve(uri);
        RegexEngine custom = new JdkRegexEngine();
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_07, custom);
        SchemaPlan local = registry.resolve(uri);
        assertNotNull(local);
        assertNotSame(global, local);
        assertSame(local, registry.resolve(uri));
        assertTrue(local.isValid(Collections.singletonMap("type", "string")));
    }

    @ValidJsonSchema(
            value = "{\"properties\":{\"value\":{\"$ref\":\"https://example.test/regex-external\"}}}",
            externalSchemas = "{\"https://example.test/regex-external\":{\"pattern\":\"abc\"}}")
    public static class Bean {
        public String value = "abc";
    }

    @Test
    void scopedAnnotationRegistryPreservesGuard() {
        IllegalArgumentException rejection = new IllegalArgumentException("blocked by policy");
        RegexEngine guarded = new JdkRegexEngine().guardedBy((expression, flags) -> {
            if ("abc".equals(expression)) throw rejection;
        });
        SchemaValidator validator = new SchemaValidator(
                new SchemaLoader.Local(SchemaRegistry.DEFAULT_JSON_SCHEMA_DIR),
                new SchemaRegistry(SchemaDialect.DRAFT_2020_12, guarded), true);
        SchemaException error = assertThrows(SchemaException.class, () -> validator.validate(new Bean()));
        assertSame(rejection, error.getCause());
    }
}
