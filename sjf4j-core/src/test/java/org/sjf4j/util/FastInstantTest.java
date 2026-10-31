package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FastInstantTest {

    @Test
    void canonicalUtcShapesAndFractionalPrecisionMatchJdk() {
        for (String value : new String[]{
                "0000-01-01T00:00:00Z", "1970-01-01T00:00:00Z",
                "2026-10-31T12:34:56Z", "2000-02-29T23:59:59Z",
                "2026-10-31T12:34:56.1Z", "2026-10-31T12:34:56.12Z",
                "2026-10-31T12:34:56.123Z", "2026-10-31T12:34:56.1234Z",
                "2026-10-31T12:34:56.12345Z", "2026-10-31T12:34:56.123456Z",
                "2026-10-31T12:34:56.1234567Z",
                "2026-10-31T12:34:56.12345678Z",
                "2026-10-31T12:34:56.123456789Z",
                "9999-12-31T23:59:59.999999999Z"}) {
            assertMatchesJdk(value);
        }
    }

    @Test
    void specialJdkLegalCasesUseFallback() {
        for (String value : new String[]{
                "2026-10-31T12:34:56.Z", "2026-10-31T23:59:60Z",
                "2026-10-31T24:00:00Z", "2026-10-31t12:34:56z",
                "2026-10-31T12:34:56+01:00", "2026-10-31T12:34:56.123-07:00",
                "+10000-10-31T12:34:56Z", "-0001-12-31T12:34:56Z"}) {
            assertMatchesJdk(value);
        }
    }

    @Test
    void invalidInputsRetainDateTimeParseException() {
        for (String value : new String[]{
                "", "2026-10-31", "2026-10-31T12:34Z",
                "2026-02-30T12:34:56Z", "2025-02-29T12:34:56Z",
                "2026-13-01T12:34:56Z", "2026-10-31T25:34:56Z",
                "2026-10-31T12:60:56Z", "2026-10-31T12:34:61Z",
                "2026-10-31T12:34:56.1234567890Z",
                "2026-10-31T12:34:56,123Z", "2026-10-31T12:34:56X",
                "2026-10-31T12:34:56", "2026-10-31T12:34:5AZ"}) {
            assertThrows(DateTimeParseException.class, () -> Instant.parse(value), value);
            assertThrows(DateTimeParseException.class, () -> FastInstant.parse(value), value);
            assertThrows(DateTimeParseException.class,
                    () -> FastInstant.parse(value.toCharArray(), 0, value.length()), value);
        }
    }

    @Test
    void randomValidUtcTimestampsMatchJdk() {
        Random random = new Random(20261031L);
        for (int i = 0; i < 2000; i++) {
            Instant instant = LocalDateTime.of(random.nextInt(10000), 1 + random.nextInt(12),
                    1, random.nextInt(24), random.nextInt(60), random.nextInt(60),
                    random.nextInt(1_000_000_000)).toInstant(ZoneOffset.UTC);
            assertMatchesJdk(instant.toString());
        }
    }

    @Test
    void charSliceRequiresValidBounds() {
        char[] chars = "2026-10-31T12:34:56Z".toCharArray();
        assertEquals(Instant.parse("2026-10-31T12:34:56Z"),
                FastInstant.parse(chars, 0, chars.length));
        assertThrows(IndexOutOfBoundsException.class,
                () -> FastInstant.parse(chars, 1, chars.length));
        assertThrows(IndexOutOfBoundsException.class,
                () -> FastInstant.parse(chars, -1, chars.length));
    }

    private static void assertMatchesJdk(String text) {
        Instant expected = Instant.parse(text);
        assertEquals(expected, FastInstant.parse(text), text);
        char[] padded = ("prefix" + text + "suffix").toCharArray();
        assertEquals(expected, FastInstant.parse(padded, 6, text.length()), text);
    }
}
