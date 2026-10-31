package org.sjf4j.util;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Fast ISO-8601 LocalDate parser for the common ten-character yyyy-MM-dd form.
 * Less common valid ISO dates and invalid input are delegated to the JDK parser,
 * preserving its accepted syntax and DateTimeParseException behavior.
 */
public final class FastLocalDate {

    private FastLocalDate() {
    }

    public static LocalDate parse(String text) {
        if (text != null && text.length() == 10
                && text.charAt(4) == '-' && text.charAt(7) == '-'
                && digit(text.charAt(0)) && digit(text.charAt(1))
                && digit(text.charAt(2)) && digit(text.charAt(3))
                && digit(text.charAt(5)) && digit(text.charAt(6))
                && digit(text.charAt(8)) && digit(text.charAt(9))) {
            int year = (text.charAt(0) - '0') * 1000
                    + (text.charAt(1) - '0') * 100
                    + (text.charAt(2) - '0') * 10 + text.charAt(3) - '0';
            int month = (text.charAt(5) - '0') * 10 + text.charAt(6) - '0';
            int day = (text.charAt(8) - '0') * 10 + text.charAt(9) - '0';
            try {
                return LocalDate.of(year, month, day);
            } catch (DateTimeException ignored) {
                // Reparse through the JDK to preserve DateTimeParseException.
            }
        }
        return LocalDate.parse(text);
    }

    /**
     * Parses directly from a reusable character buffer without allocating an
     * intermediate String for canonical dates. The caller owns the buffer.
     */
    public static LocalDate parse(char[] chars, int offset, int length) {
        if (length == 10
                && offset >= 0 && offset <= chars.length - length
                && chars[offset + 4] == '-' && chars[offset + 7] == '-'
                && digit(chars[offset]) && digit(chars[offset + 1])
                && digit(chars[offset + 2]) && digit(chars[offset + 3])
                && digit(chars[offset + 5]) && digit(chars[offset + 6])
                && digit(chars[offset + 8]) && digit(chars[offset + 9])) {
            int year = (chars[offset] - '0') * 1000
                    + (chars[offset + 1] - '0') * 100
                    + (chars[offset + 2] - '0') * 10 + chars[offset + 3] - '0';
            int month = (chars[offset + 5] - '0') * 10 + chars[offset + 6] - '0';
            int day = (chars[offset + 8] - '0') * 10 + chars[offset + 9] - '0';
            try {
                return LocalDate.of(year, month, day);
            } catch (DateTimeException ignored) {
                // Reparse through the JDK to preserve DateTimeParseException.
            }
        }
        return LocalDate.parse(new String(chars, offset, length));
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
