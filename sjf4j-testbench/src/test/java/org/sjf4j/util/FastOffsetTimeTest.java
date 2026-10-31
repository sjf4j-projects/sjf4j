package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FastOffsetTimeTest {

    @Test
    void canonicalOffsetsAndFractionPrecision() {
        for (String text : new String[]{
                "00:00Z", "23:59:59Z", "12:34+00:00", "12:34-00:00",
                "12:34+05:30", "12:34:56-07:00",
                "12:34:56+18:00", "12:34:56-18:00",
                "12:34:56.1+05:30", "12:34:56.12+05:30",
                "12:34:56.123+05:30", "12:34:56.1234+05:30",
                "12:34:56.12345+05:30", "12:34:56.123456+05:30",
                "12:34:56.1234567+05:30",
                "12:34:56.12345678+05:30",
                "12:34:56.123456789+05:30",
                "12:34:56.123456789Z"}) {
            assertMatchesJdk(text);
        }
    }

    @Test
    void rareLegalFormsAndUnusualSpellingUseJdk() {
        for (String text : new String[]{
                "12:34:56+01", "12:34:56+01:02:03",
                "12:34:56-00:00:12",
                "12:34:56.123+01",
                "12:34:56.123456789+00:01:02",
                "12:34:56.1234567890Z",
                "12:34:56.123456789z",
                "12:34:56.Z",
                "12:34:56,123Z",
                "12:34:56+19:00"}) {
            assertSameOutcomeAsJdk(text);
        }
    }

    @Test
    void invalidInputRetainsJdkExceptions() {
        for (String text : new String[]{
                "", "12:34", "12:34:56", "12:34:56X",
                "12:34:56+18:01", "12:34:56-19:00",
                "12:34:56+01:60", "12:34:56+0x:00",
                "24:00Z", "12:60Z", "12:34:60Z",
                "12:34:56.1234567890Z"}) {
            assertThrows(DateTimeParseException.class, () -> OffsetTime.parse(text), text);
            assertThrows(DateTimeParseException.class, () -> FastOffsetTime.parse(text), text);
            assertThrows(DateTimeParseException.class,
                    () -> FastOffsetTime.parse(text.toCharArray(), 0, text.length()), text);
        }
    }

    @Test
    void twoThousandRandomOffsetsAndTimes() {
        Random random = new Random(20261031L);
        for (int i = 0; i < 2000; i++) {
            int minutes = random.nextInt(2161) - 1080;
            OffsetTime time = LocalTime.of(random.nextInt(24), random.nextInt(60),
                    random.nextInt(60), random.nextInt(1_000_000_000))
                    .atOffset(ZoneOffset.ofTotalSeconds(minutes * 60));
            assertMatchesJdk(time.toString());
        }
    }

    @Test
    void sliceAndInvalidBounds() {
        String text = "12:34:56.123456789-07:30";
        char[] padded = ("before" + text + "after").toCharArray();
        assertEquals(OffsetTime.parse(text), FastOffsetTime.parse(padded, 6, text.length()));
        assertThrows(IndexOutOfBoundsException.class,
                () -> FastOffsetTime.parse(padded, -1, text.length()));
        assertThrows(IndexOutOfBoundsException.class,
                () -> FastOffsetTime.parse(padded, padded.length - 1, text.length()));
    }

    private static void assertMatchesJdk(String text) {
        OffsetTime expected = OffsetTime.parse(text);
        assertEquals(expected, FastOffsetTime.parse(text), text);
        char[] padded = ("prefix" + text + "suffix").toCharArray();
        assertEquals(expected, FastOffsetTime.parse(padded, 6, text.length()), text);
    }

    private static void assertSameOutcomeAsJdk(String text) {
        try {
            assertMatchesJdk(text);
        } catch (DateTimeParseException expected) {
            assertThrows(DateTimeParseException.class, () -> FastOffsetTime.parse(text), text);
            assertThrows(DateTimeParseException.class,
                    () -> FastOffsetTime.parse(text.toCharArray(), 0, text.length()), text);
        }
    }
}
