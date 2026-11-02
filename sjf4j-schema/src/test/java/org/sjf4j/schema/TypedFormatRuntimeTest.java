package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Typed format fast paths must preserve the standard string validator semantics. */
class TypedFormatRuntimeTest {
    private static final UUID UUID_VALUE = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

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

    private static SchemaPlan plan(String format) {
        return JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"" + format + "\"}").createPlan();
    }

    @Test
    void uuidCodecPreservesRawStringAndTypedFormatHint() {
        Instance instance = Instance.infer(UUID_VALUE);
        assertEquals(JsonType.STRING, instance.jsonType());
        assertTrue(instance.converted());
        assertSame(UUID_VALUE, instance.node());
        assertEquals(UUID_VALUE.toString(), instance.raw());
    }

    @Test
    void uuidFormatChecksUseKnownBuiltinRepresentation() {
        SchemaPlan uuid = plan("uuid");
        assertTrue(uuid.isValid(UUID_VALUE, true));
        assertTrue(uuid.isValid(UUID_VALUE, false));
        assertTrue(uuid.isValid(UUID_VALUE.toString(), true));
        assertFalse(uuid.isValid("not-a-uuid", true));
        assertTrue(uuid.isValid("not-a-uuid", false));

        SchemaPlan length = JsonSchema.fromJson(
                "{\"type\":\"string\",\"format\":\"uuid\",\"minLength\":37}").createPlan();
        assertFalse(length.isValid(UUID_VALUE, true));
        assertFalse(length.isValid(UUID_VALUE, false));
        assertFalse(uuid.isValid((Object) null, true));
    }

    @Test
    void uriAndUriReferenceRetainAsciiAuthorityAndAbsoluteRules() {
        SchemaPlan uri = plan("uri");
        SchemaPlan ref = plan("uri-reference");
        URI absolute = URI.create("https://example.com/a?x=1");
        URI relative = URI.create("../a?x=1");
        URI badPort = URI.create("https://example.com:bad/path");
        URI unicode = URI.create("https://exämple.com/path");
        URI empty = URI.create("");

        for (URI value : new URI[]{absolute, relative, badPort, unicode, empty}) {
            assertEquals(uri.isValid(value.toString(), true), uri.isValid(value, true), value.toString());
            assertEquals(ref.isValid(value.toString(), true), ref.isValid(value, true), value.toString());
        }
        assertTrue(uri.isValid(absolute, true));
        assertFalse(uri.isValid(relative, true));
        assertTrue(ref.isValid(relative, true));
        assertFalse(uri.isValid(badPort, true));
        assertFalse(ref.isValid(badPort, true));
        assertFalse(uri.isValid(unicode, true));
        assertFalse(ref.isValid(empty, true));
        assertTrue(uri.isValid(relative, false));
        assertTrue(ref.isValid(badPort, false));
    }

    @Test
    void typedPropertiesPreserveOtherStringKeywords() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"type\":\"object\",\"properties\":{"
                + "\"id\":{\"type\":\"string\",\"format\":\"uuid\",\"maxLength\":35},"
                + "\"link\":{\"type\":\"string\",\"format\":\"uri\"}}}").createPlan();
        assertFalse(plan.isValid(new Holder(UUID_VALUE, URI.create("https://example.com")), true));

        SchemaPlan ordinary = JsonSchema.fromJson(
                "{\"type\":\"object\",\"properties\":{"
                + "\"id\":{\"type\":\"string\",\"format\":\"uuid\"},"
                + "\"link\":{\"type\":\"string\",\"format\":\"uri\"}}}").createPlan();
        assertTrue(ordinary.isValid(new Holder(UUID_VALUE, URI.create("https://example.com")), true));
        assertFalse(ordinary.isValid(new Holder(UUID_VALUE, URI.create("../relative")), true));
    }
}
