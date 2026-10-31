package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;

class FastLocalTimeTest {

    @Test
    void canonicalAndFractionalTimesMatchJdk() {
        for (String time : new String[]{
                "00:00", "23:59", "12:34", "00:00:00", "23:59:59",
                "12:34:56.1", "12:34:56.12", "12:34:56.123",
                "12:34:56.1234", "12:34:56.12345", "12:34:56.123456",
                "12:34:56.1234567", "12:34:56.12345678",
                "12:34:56.123456789", "12:34:56.000000001"}) {
            LocalTime expected = LocalTime.parse(time);
            assertEquals(expected, FastLocalTime.parse(time), time);
            char[] chars = ("ab" + time + "xy").toCharArray();
            assertEquals(expected, FastLocalTime.parse(chars, 2, time.length()), time);
        }
    }

    @Test
    void invalidInputsKeepJdkExceptionBehavior() {
        for (String time : new String[]{
                "", "1:23", "12:3", "24:00", "23:60", "12:34:60",
                "12:34:56.", "12:34:56.1234567890", "12:34:56,123",
                "12:34:56.X", "12:34:5A", "12-34-56",
                "12:34:56Z", "12:34:56+01:00"}) {
            assertThrows(DateTimeParseException.class, () -> LocalTime.parse(time), time);
            assertThrows(DateTimeParseException.class, () -> FastLocalTime.parse(time), time);
            assertThrows(DateTimeParseException.class,
                    () -> FastLocalTime.parse(time.toCharArray(), 0, time.length()), time);
        }
    }

    @Test
    void bufferSliceBoundsFallBackWithoutUnsafeAccess() {
        char[] chars = "12:34:56".toCharArray();
        assertEquals(LocalTime.of(12, 34, 56), FastLocalTime.parse(chars, 0, chars.length));
        assertThrows(IndexOutOfBoundsException.class, () -> FastLocalTime.parse(chars, 1, 8));
        assertThrows(IndexOutOfBoundsException.class, () -> FastLocalTime.parse(chars, -1, 8));
    }
}
