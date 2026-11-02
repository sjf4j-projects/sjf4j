package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiled typed format checks must agree with the runtime schema plan. */
class CompiledValidatorTypedFormatTest {
    private static final String UUID_SCHEMA = "{\"type\":\"string\",\"format\":\"uuid\"}";
    private static final String URI_SCHEMA = "{\"type\":\"string\",\"format\":\"uri\"}";
    private static final String REFERENCE_SCHEMA = "{\"type\":\"string\",\"format\":\"uri-reference\"}";
    private static final String UUID_LENGTH = "{\"type\":\"string\",\"format\":\"uuid\",\"minLength\":37}";

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(UUID_SCHEMA) boolean uuid(UUID value, boolean strictFormat);
        @ValidJsonSchema(URI_SCHEMA) boolean uri(URI value, boolean strictFormat);
        @ValidJsonSchema(REFERENCE_SCHEMA) boolean reference(URI value, boolean strictFormat);
        @ValidJsonSchema(UUID_LENGTH) boolean uuidLength(UUID value, boolean strictFormat);
    }

    public static final class Holder {
        private final UUID id;
        private final URI link;

        public Holder() { this(null, null); }

        public Holder(UUID id, URI link) {
            this.id = id;
            this.link = link;
        }
        public UUID getId() { return id; }
        public URI getLink() { return link; }
    }

    private static final String PROPERTY_SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"id\":{\"type\":\"string\",\"format\":\"uuid\"},"
            + "\"link\":{\"type\":\"string\",\"format\":\"uri\"}}}";

    @CompiledValidator(fallback = false)
    public interface Properties {
        @ValidJsonSchema(PROPERTY_SCHEMA) boolean valid(Holder value, boolean strictFormat);
    }

    @Test
    void directUuidAndUriChecksMatchRuntime() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan uuid = JsonSchema.fromJson(UUID_SCHEMA).createPlan();
        SchemaPlan uri = JsonSchema.fromJson(URI_SCHEMA).createPlan();
        SchemaPlan ref = JsonSchema.fromJson(REFERENCE_SCHEMA).createPlan();
        SchemaPlan length = JsonSchema.fromJson(UUID_LENGTH).createPlan();
        UUID value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        for (boolean strict : new boolean[]{false, true}) {
            assertEquals(uuid.isValid(value, strict), compiled.uuid(value, strict));
            assertEquals(length.isValid(value, strict), compiled.uuidLength(value, strict));
            assertTrue(compiled.uuid(null, strict)); // Existing compiled root-null convention.
            for (URI candidate : new URI[]{
                    URI.create("https://example.org/path"),
                    URI.create("../relative"),
                    URI.create("https://example.org:bad/path"),
                    URI.create("https://exämple.org/path"),
                    URI.create("")}) {
                assertEquals(uri.isValid(candidate, strict), compiled.uri(candidate, strict), candidate.toString());
                assertEquals(ref.isValid(candidate, strict), compiled.reference(candidate, strict), candidate.toString());
            }
            assertTrue(compiled.uri(null, strict));
        }
        assertTrue(compiled.uuid(value, true));
        assertFalse(compiled.uuidLength(value, true));
        assertFalse(compiled.uri(URI.create("../relative"), true));
        assertTrue(compiled.reference(URI.create("../relative"), true));
    }

    @Test
    void typedPojoPropertiesMatchRuntime() {
        Properties compiled = CompiledInstances.create(Properties.class);
        SchemaPlan reference = JsonSchema.fromJson(PROPERTY_SCHEMA).createPlan();
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Holder absolute = new Holder(id, URI.create("https://example.org"));
        Holder relative = new Holder(id, URI.create("../relative"));
        Holder badPort = new Holder(id, URI.create("https://example.org:bad/path"));
        assertEquals(reference.isValid(absolute, false), compiled.valid(absolute, false), "absolute non-strict");
        assertEquals(reference.isValid(absolute, true), compiled.valid(absolute, true), "absolute strict");
        assertEquals(reference.isValid(relative, false), compiled.valid(relative, false), "relative non-strict");
        assertEquals(reference.isValid(relative, true), compiled.valid(relative, true), "relative strict");
        assertEquals(reference.isValid(badPort, false), compiled.valid(badPort, false), "bad port non-strict");
        assertEquals(reference.isValid(badPort, true), compiled.valid(badPort, true), "bad port strict");
    }
}
