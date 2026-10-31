package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FastOffsetDateTimeTest {

    @Test
    void canonicalShapesAndAllFractionPrecisions() {
        for (String value : new String[]{
                "0000-01-01T00:00Z",
                "1970-01-01T00:00:00+00:00",
                "2000-02-29T23:59:59-18:00",
                "9999-12-31T23:59:59.999999999+18:00",
                "2026-10-31T12:34+08:00",
                "2026-10-31T12:34:56+05:30",
                "2026-10-31T12:34:56-03:45",
                "2026-10-31T12:34:56Z",
                "2026-10-31T12:34:56.1+05:30",
                "2026-10-31T12:34:56.12+05:30",
                "2026-10-31T12:34:56.123+05:30",
                "2026-10-31T12:34:56.1234+05:30",
                "2026-10-31T12:34:56.12345+05:30",
                "2026-10-31T12:34:56.123456+05:30",
                "2026-10-31T12:34:56.1234567+05:30",
                "2026-10-31T12:34:56.12345678+05:30",
                "2026-10-31T12:34:56.123456789+05:30",
                "2026-10-31T12:34:56.123456789Z"}) {
            assertMatchesJdk(value);
        }
    }

    @Test
    void unusualValidIsoFormsRetainJdkBehavior() {
        for (String value : new String[]{
                "2026-10-31T12:34:56+01",
                "2026-10-31T12:34:56+01:02:03",
                "2026-10-31T12:34:56-00:00",
                "2026-10-31t12:34:56z",
                "2026-10-31T12:34:56.123+01",
                "+10000-01-01T12:34:56Z",
                "-0001-01-01T12:34:56Z",
                "2026-10-31T12:34:56.Z",
                "2026-10-31T24:00Z",
                "2026-10-31T23:59:60Z"}) {
            assertSameOutcomeAsJdk(value);
        }
    }

    @Test
    void invalidInputFollowsJdkExceptionContract() {
        for (String value : new String[]{
                "", "2026-10-31", "2026-10-31T12:34",
                "2026-10-31T12:34+19:00", "2026-10-31T12:34+18:01",
                "2026-10-31T12:34+01:60", "2026-10-31T12:34+0x:30",
                "2026-10-31T12:34:56.1234567890Z",
                "2026-10-31T12:34:56.123456789X",
                "2026-10-31T12:34:56,123Z",
                "2025-02-29T12:34Z", "2026-02-30T12:34Z",
                "2026-10-31T25:34Z", "2026-10-31T12:60Z"}) {
            assertThrows(DateTimeParseException.class, () -> OffsetDateTime.parse(value), value);
            assertThrows(DateTimeParseException.class, () -> FastOffsetDateTime.parse(value), value);
            assertThrows(DateTimeParseException.class,
                    () -> FastOffsetDateTime.parse(value.toCharArray(), 0, value.length()), value);
        }
    }

    @Test
    void randomCanonicalValuesMatchJdk() {
        Random random = new Random(20261031L);
        for (int i = 0; i < 2000; i++) {
            int offsetMinutes = random.nextInt(2161) - 1080;
            OffsetDateTime source = LocalDateTime.of(random.nextInt(10000),
                    1 + random.nextInt(12), 1 + random.nextInt(28),
                    random.nextInt(24), random.nextInt(60),
                    random.nextInt(60), random.nextInt(1_000_000_000))
                    .atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * 60));
            assertMatchesJdk(source.toString());
        }
    }

    @Test
    void directBufferOffsetAndBounds() {
        String text = "2026-10-31T12:34:56.123456789-07:30";
        char[] chars = ("prefix" + text + "suffix").toCharArray();
        assertEquals(OffsetDateTime.parse(text), FastOffsetDateTime.parse(chars, 6, text.length()));
        assertThrows(IndexOutOfBoundsException.class,
                () -> FastOffsetDateTime.parse(chars, -1, text.length()));
        assertThrows(IndexOutOfBoundsException.class,
                () -> FastOffsetDateTime.parse(chars, chars.length - 2, text.length()));
    }

    private static void assertMatchesJdk(String text) {
        OffsetDateTime expected = OffsetDateTime.parse(text);
        assertEquals(expected, FastOffsetDateTime.parse(text), text);
        char[] chars = ("before" + text + "after").toCharArray();
        assertEquals(expected, FastOffsetDateTime.parse(chars, 6, text.length()), text);
    }

    private static void assertSameOutcomeAsJdk(String text) {
        try {
            assertMatchesJdk(text);
        } catch (DateTimeParseException ex) {
            assertThrows(DateTimeParseException.class, () -> FastOffsetDateTime.parse(text), text);
            char[] chars = text.toCharArray();
            assertThrows(DateTimeParseException.class,
                    () -> FastOffsetDateTime.parse(chars, 0, chars.length), text);
        }
    }
}
