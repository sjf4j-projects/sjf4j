package org.sjf4j.util;

import java.time.DateTimeException;
import java.time.LocalTime;

/**
 * Fast ISO-8601 LocalTime parser for HH:mm, HH:mm:ss and HH:mm:ss.fraction
 * (one through nine fractional digits). Other inputs fall back to the JDK
 * parser to preserve its syntax and exception behavior.
 */
public final class FastLocalTime {
    private static final int[] NANO_SCALE = {
            1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000
    };

    private FastLocalTime() {
    }

    public static LocalTime parse(String text) {
        if (text != null) {
            int length = text.length();
            if ((length == 5 || length == 8 || (length >= 10 && length <= 18))
                    && text.charAt(2) == ':'
                    && digit(text.charAt(0)) && digit(text.charAt(1))
                    && digit(text.charAt(3)) && digit(text.charAt(4))) {
                int hour = (text.charAt(0) - '0') * 10 + text.charAt(1) - '0';
                int minute = (text.charAt(3) - '0') * 10 + text.charAt(4) - '0';
                int second = 0;
                int nano = 0;
                boolean valid = true;
                if (length > 5) {
                    valid = text.charAt(5) == ':'
                            && digit(text.charAt(6)) && digit(text.charAt(7));
                    if (valid) {
                        second = (text.charAt(6) - '0') * 10 + text.charAt(7) - '0';
                    }
                }
                if (valid && length > 8) {
                    valid = text.charAt(8) == '.';
                    for (int i = 9; valid && i < length; i++) {
                        char c = text.charAt(i);
                        valid = digit(c);
                        if (valid) nano = nano * 10 + c - '0';
                    }
                    if (valid) nano *= NANO_SCALE[18 - length];
                }
                if (valid) {
                    try {
                        return LocalTime.of(hour, minute, second, nano);
                    } catch (DateTimeException ignored) {
                        // Let JDK parsing supply the established exception.
                    }
                }
            }
        }
        return LocalTime.parse(text);
    }

    /**
     * Reads canonical ISO time directly from a reusable character buffer.
     * The caller retains ownership of the buffer.
     */
    public static LocalTime parse(char[] chars, int offset, int length) {
        if (chars != null && (length == 5 || length == 8 || (length >= 10 && length <= 18))
                && offset >= 0 && offset <= chars.length - length
                && chars[offset + 2] == ':'
                && digit(chars[offset]) && digit(chars[offset + 1])
                && digit(chars[offset + 3]) && digit(chars[offset + 4])) {
            int hour = (chars[offset] - '0') * 10 + chars[offset + 1] - '0';
            int minute = (chars[offset + 3] - '0') * 10 + chars[offset + 4] - '0';
            int second = 0;
            int nano = 0;
            boolean valid = true;
            if (length > 5) {
                valid = chars[offset + 5] == ':'
                        && digit(chars[offset + 6]) && digit(chars[offset + 7]);
                if (valid) {
                    second = (chars[offset + 6] - '0') * 10 + chars[offset + 7] - '0';
                }
            }
            if (valid && length > 8) {
                valid = chars[offset + 8] == '.';
                for (int i = 9; valid && i < length; i++) {
                    char c = chars[offset + i];
                    valid = digit(c);
                    if (valid) nano = nano * 10 + c - '0';
                }
                if (valid) nano *= NANO_SCALE[18 - length];
            }
            if (valid) {
                try {
                    return LocalTime.of(hour, minute, second, nano);
                } catch (DateTimeException ignored) {
                    // Let JDK parsing supply the established exception.
                }
            }
        }
        return LocalTime.parse(new String(chars, offset, length));
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
