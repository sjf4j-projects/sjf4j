package org.sjf4j.util;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Fast parser for canonical ISO UTC instants with four-digit years:
 * yyyy-MM-dd'T'HH:mm:ss'Z' and yyyy-MM-dd'T'HH:mm:ss.fraction'Z'.
 *
 * Other legal ISO forms (offsets, leap seconds, 24:00, signed years and
 * noncanonical case) and malformed input use Instant.parse as fallback.
 */
public final class FastInstant {

    private static final int[] NANO_SCALE = {
            1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000
    };

    private FastInstant() {
    }

    public static Instant parse(String text) {
        if (text != null) {
            final int length = text.length();
            if ((length == 20 || (length >= 22 && length <= 30))
                    && text.charAt(4) == '-' && text.charAt(7) == '-'
                    && text.charAt(10) == 'T' && text.charAt(13) == ':'
                    && text.charAt(16) == ':' && text.charAt(length - 1) == 'Z'
                    && digit(text.charAt(0)) && digit(text.charAt(1))
                    && digit(text.charAt(2)) && digit(text.charAt(3))
                    && digit(text.charAt(5)) && digit(text.charAt(6))
                    && digit(text.charAt(8)) && digit(text.charAt(9))
                    && digit(text.charAt(11)) && digit(text.charAt(12))
                    && digit(text.charAt(14)) && digit(text.charAt(15))
                    && digit(text.charAt(17)) && digit(text.charAt(18))) {
                int nano = 0;
                boolean valid = true;
                if (length > 20) {
                    valid = text.charAt(19) == '.';
                    for (int i = 20; valid && i < length - 1; i++) {
                        char c = text.charAt(i);
                        valid = digit(c);
                        if (valid) nano = nano * 10 + c - '0';
                    }
                    if (valid) nano *= NANO_SCALE[30 - length];
                }
                if (valid) {
                    int year = (text.charAt(0) - '0') * 1000
                            + (text.charAt(1) - '0') * 100
                            + (text.charAt(2) - '0') * 10 + text.charAt(3) - '0';
                    try {
                        long epochDay = LocalDate.of(year,
                                number(text.charAt(5), text.charAt(6)),
                                number(text.charAt(8), text.charAt(9))).toEpochDay();
                        int hour = number(text.charAt(11), text.charAt(12));
                        int minute = number(text.charAt(14), text.charAt(15));
                        int second = number(text.charAt(17), text.charAt(18));
                        if (hour < 24 && minute < 60 && second < 60) {
                            return Instant.ofEpochSecond(epochDay * 86_400L
                                    + hour * 3600L + minute * 60L + second, nano);
                        }
                    } catch (DateTimeException ignored) {
                        // Delegate to JDK for standard exception details.
                    }
                }
            }
        }
        return Instant.parse(text);
    }

    /**
     * Decodes directly from a caller-owned character buffer when canonical.
     */
    public static Instant parse(char[] chars, int offset, int length) {
        if (chars != null && (length == 20 || (length >= 22 && length <= 30))
                && offset >= 0 && offset <= chars.length - length
                && chars[offset + 4] == '-' && chars[offset + 7] == '-'
                && chars[offset + 10] == 'T' && chars[offset + 13] == ':'
                && chars[offset + 16] == ':' && chars[offset + length - 1] == 'Z'
                && digit(chars[offset]) && digit(chars[offset + 1])
                && digit(chars[offset + 2]) && digit(chars[offset + 3])
                && digit(chars[offset + 5]) && digit(chars[offset + 6])
                && digit(chars[offset + 8]) && digit(chars[offset + 9])
                && digit(chars[offset + 11]) && digit(chars[offset + 12])
                && digit(chars[offset + 14]) && digit(chars[offset + 15])
                && digit(chars[offset + 17]) && digit(chars[offset + 18])) {
            int nano = 0;
            boolean valid = true;
            if (length > 20) {
                valid = chars[offset + 19] == '.';
                for (int i = 20; valid && i < length - 1; i++) {
                    char c = chars[offset + i];
                    valid = digit(c);
                    if (valid) nano = nano * 10 + c - '0';
                }
                if (valid) nano *= NANO_SCALE[30 - length];
            }
            if (valid) {
                int year = (chars[offset] - '0') * 1000
                        + (chars[offset + 1] - '0') * 100
                        + (chars[offset + 2] - '0') * 10 + chars[offset + 3] - '0';
                try {
                    long epochDay = LocalDate.of(year,
                            number(chars[offset + 5], chars[offset + 6]),
                            number(chars[offset + 8], chars[offset + 9])).toEpochDay();
                    int hour = number(chars[offset + 11], chars[offset + 12]);
                    int minute = number(chars[offset + 14], chars[offset + 15]);
                    int second = number(chars[offset + 17], chars[offset + 18]);
                    if (hour < 24 && minute < 60 && second < 60) {
                        return Instant.ofEpochSecond(epochDay * 86_400L
                                + hour * 3600L + minute * 60L + second, nano);
                    }
                } catch (DateTimeException ignored) {
                    // Delegate to JDK for standard exception details.
                }
            }
        }
        return Instant.parse(new String(chars, offset, length));
    }

    private static int number(char tens, char ones) {
        return (tens - '0') * 10 + ones - '0';
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
