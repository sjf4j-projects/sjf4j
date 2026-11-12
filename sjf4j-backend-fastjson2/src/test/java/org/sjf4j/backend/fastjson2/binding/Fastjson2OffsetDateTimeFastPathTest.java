package org.sjf4j.backend.fastjson2.binding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Correctness guard for the experimental shared OffsetDateTime fast path. */
class Fastjson2OffsetDateTimeFastPathTest {

    private final Fastjson2Binder binder = new Fastjson2Binder();

    @Test
    void canonicalFormsPreserveDateTimeAndExplicitOffset() {
        for (String value : new String[]{
                "1970-01-01T00:00Z",
                "2026-10-31T12:34Z",
                "2026-10-31T12:34+08:00",
                "2026-10-31T12:34:56-05:30",
                "2026-10-31T12:34:56.1Z",
                "2026-10-31T12:34:56.123+05:30",
                "2026-10-31T12:34:56.123456-07:00",
                "2026-10-31T12:34:56.123456789Z"}) {
            String json = "\"" + value + "\"";
            OffsetDateTime expected = OffsetDateTime.parse(value);
            assertEquals(expected, binder.readNode(json, OffsetDateTime.class), value);
            assertEquals(expected, binder.readNode(json.getBytes(StandardCharsets.UTF_8),
                    OffsetDateTime.class), "UTF-8: " + value);
        }
        assertNull(binder.readNode("null", OffsetDateTime.class));
        assertNull(binder.readNode("null".getBytes(StandardCharsets.UTF_8), OffsetDateTime.class));
    }

    @Test
    void consumesArrayElementsIncludingNull() throws IOException {
        try (Fastjson2Parser parser = binder.createParser(
                "[\"2026-10-31T12:34:56+05:30\",null,\"2026-10-31T12:34Z\"]")) {
            parser.start();
            parser.readArrayStart();
            assertEquals(OffsetDateTime.parse("2026-10-31T12:34:56+05:30"),
                    parser.readOffsetDateTime());
            assertNull(parser.readOffsetDateTime());
            assertEquals(OffsetDateTime.parse("2026-10-31T12:34Z"),
                    parser.readOffsetDateTime());
            parser.readArrayEnd();
            parser.end();
        }
    }

    @Test
    void unsupportedCanonicalShapesFallbackToJdkParser() {
        String hourOffset = "2026-10-31T12:34:56+05";
        assertEquals(OffsetDateTime.parse(hourOffset),
                binder.readNode("\"" + hourOffset + "\"", OffsetDateTime.class));
        assertThrows(RuntimeException.class,
                () -> binder.readNode("\"2026-02-30T12:34:56Z\"", OffsetDateTime.class));
    }
}
