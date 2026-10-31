package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FastLocalDateTimeTest {

    @Test
    void canonicalIsoAndNanosecondPrecisionMatchJdk() {
        for (String dateTime : new String[]{
                "0000-01-01T00:00", "2026-10-31T12:34", "2026-10-31T12:34:56",
                "2000-02-29T23:59:59", "2026-10-31T12:34:56.1",
                "2026-10-31T12:34:56.12", "2026-10-31T12:34:56.123",
                "2026-10-31T12:34:56.1234", "2026-10-31T12:34:56.12345",
                "2026-10-31T12:34:56.123456", "2026-10-31T12:34:56.1234567",
                "2026-10-31T12:34:56.12345678", "2026-10-31T12:34:56.123456789",
                "2026-10-31T12:34:56.000000001",
                "9999-12-31T23:59:59.999999999"}) {
            assertMatchesJdk(dateTime);
        }
    }

    @Test
    void uncommonJdkSupportedFormatsUseFallback() {
        for (String dateTime : new String[]{
                "2026-10-31T12:34:56.", "2026-10-31t12:34:56",
                "+10000-10-31T12:34:56", "-0001-12-31T23:59:59",
                "2026-10-31T12:34:56.000000000"}) {
            assertMatchesJdk(dateTime);
        }
    }

    @Test
    void malformedAndInvalidDatesPreserveJdkExceptions() {
        for (String dateTime : new String[]{
                "", "2026-10-31", "2026-10-31T1:34",
                "2026-10-31T12:3", "2026-10-31T24:00",
                "2026-10-31T12:60", "2026-10-31T12:34:60",
                "2026-02-30T12:34:56", "2025-02-29T12:34",
                "2026-13-01T12:34", "2026-10-31T12:34:56.1234567890",
                "2026-10-31T12:34:56X", "2026-10-31T12:34:56Z",
                "2026-10-31T12:34:56+01:00", "2026-10-31 12:34:56",
                "2026-10-31T12:34:5A", "2026/10/31T12:34:56"}) {
            assertThrows(DateTimeParseException.class, () -> LocalDateTime.parse(dateTime), dateTime);
            assertThrows(DateTimeParseException.class, () -> FastLocalDateTime.parse(dateTime), dateTime);
            assertThrows(DateTimeParseException.class,
                    () -> FastLocalDateTime.parse(dateTime.toCharArray(), 0, dateTime.length()), dateTime);
        }
    }

    @Test
    void randomizedIsoValuesMatchJdkForBothOverloads() {
        Random random = new Random(20261031L);
        for (int i = 0; i < 2000; i++) {
            LocalDateTime generated = LocalDateTime.of(
                    random.nextInt(10000), 1 + random.nextInt(12), 1, random.nextInt(24),
                    random.nextInt(60), random.nextInt(60), random.nextInt(1_000_000_000));
            assertMatchesJdk(generated.toString());
        }
    }

    @Test
    void invalidBufferSlicesCannotReadPastEnd() {
        char[] chars = "2026-10-31T12:34:56".toCharArray();
        assertEquals(LocalDateTime.of(2026, 10, 31, 12, 34, 56),
                FastLocalDateTime.parse(chars, 0, chars.length));
        assertThrows(IndexOutOfBoundsException.class,
                () -> FastLocalDateTime.parse(chars, 1, chars.length));
        assertThrows(IndexOutOfBoundsException.class,
                () -> FastLocalDateTime.parse(chars, -1, chars.length));
    }

    private static void assertMatchesJdk(String value) {
        LocalDateTime expected = LocalDateTime.parse(value);
        assertEquals(expected, FastLocalDateTime.parse(value), value);
        char[] padded = ("prefix" + value + "suffix").toCharArray();
        assertEquals(expected, FastLocalDateTime.parse(padded, 6, value.length()), value);
    }
}
