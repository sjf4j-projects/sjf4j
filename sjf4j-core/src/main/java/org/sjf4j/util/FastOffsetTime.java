package org.sjf4j.util;

import java.time.DateTimeException;
import java.time.OffsetTime;
import java.time.ZoneOffset;

/**
 * Fast path for ISO offset times HH:mm[:ss[.fraction]] followed by Z or +/-HH:mm.
 * Unusual legal spellings and invalid input use the JDK parser for compatibility.
 */
public final class FastOffsetTime {
    private static final int[] NANO_SCALE = {
            1, 10, 100, 1_000, 10_000, 100_000,
            1_000_000, 10_000_000, 100_000_000
    };

    private FastOffsetTime() {
    }

    public static OffsetTime parse(String text) {
        if (text != null) {
            int total = text.length();
            boolean utc = total > 0 && text.charAt(total - 1) == 'Z';
            boolean numericOffset = total >= 6
                    && (text.charAt(total - 6) == '+' || text.charAt(total - 6) == '-')
                    && text.charAt(total - 3) == ':';
            int timeEnd = utc ? total - 1 : total - 6;
            if ((utc || numericOffset)
                    && (timeEnd == 5 || timeEnd == 8 || (timeEnd >= 10 && timeEnd <= 18))
                    && text.charAt(2) == ':'
                    && digit(text.charAt(0)) && digit(text.charAt(1))
                    && digit(text.charAt(3)) && digit(text.charAt(4))) {
                int hour = number(text.charAt(0), text.charAt(1));
                int minute = number(text.charAt(3), text.charAt(4));
                int second = 0;
                int nano = 0;
                boolean valid = true;
                if (timeEnd > 5) {
                    valid = text.charAt(5) == ':'
                            && digit(text.charAt(6)) && digit(text.charAt(7));
                    if (valid) second = number(text.charAt(6), text.charAt(7));
                }
                if (valid && timeEnd > 8) {
                    valid = text.charAt(8) == '.';
                    for (int i = 9; valid && i < timeEnd; i++) {
                        char c = text.charAt(i);
                        valid = digit(c);
                        if (valid) nano = nano * 10 + c - '0';
                    }
                    if (valid) nano *= NANO_SCALE[18 - timeEnd];
                }
                int offsetSeconds = 0;
                if (valid && numericOffset) {
                    char hh0 = text.charAt(total - 5);
                    char hh1 = text.charAt(total - 4);
                    char mm0 = text.charAt(total - 2);
                    char mm1 = text.charAt(total - 1);
                    valid = digit(hh0) && digit(hh1) && digit(mm0) && digit(mm1);
                    if (valid) {
                        int zoneHour = number(hh0, hh1);
                        int zoneMinute = number(mm0, mm1);
                        valid = zoneHour <= 18 && zoneMinute <= 59
                                && (zoneHour < 18 || zoneMinute == 0);
                        if (valid) {
                            offsetSeconds = (zoneHour * 60 + zoneMinute) * 60;
                            if (text.charAt(total - 6) == '-') offsetSeconds = -offsetSeconds;
                        }
                    }
                }
                if (valid) {
                    try {
                        return OffsetTime.of(hour, minute, second, nano,
                                ZoneOffset.ofTotalSeconds(offsetSeconds));
                    } catch (DateTimeException ignored) {
                        // Preserve JDK exception behavior for malformed values.
                    }
                }
            }
        }
        return OffsetTime.parse(text);
    }

    /** Parses canonical input directly from a caller-owned character buffer. */
    public static OffsetTime parse(char[] chars, int offset, int length) {
        if (chars != null && offset >= 0 && length >= 0 && offset <= chars.length - length) {
            int total = length;
            boolean utc = total > 0 && chars[offset + total - 1] == 'Z';
            boolean numericOffset = total >= 6
                    && (chars[offset + total - 6] == '+' || chars[offset + total - 6] == '-')
                    && chars[offset + total - 3] == ':';
            int timeEnd = utc ? total - 1 : total - 6;
            if ((utc || numericOffset)
                    && (timeEnd == 5 || timeEnd == 8 || (timeEnd >= 10 && timeEnd <= 18))
                    && chars[offset + 2] == ':'
                    && digit(chars[offset + 0]) && digit(chars[offset + 1])
                    && digit(chars[offset + 3]) && digit(chars[offset + 4])) {
                int hour = number(chars[offset + 0], chars[offset + 1]);
                int minute = number(chars[offset + 3], chars[offset + 4]);
                int second = 0;
                int nano = 0;
                boolean valid = true;
                if (timeEnd > 5) {
                    valid = chars[offset + 5] == ':'
                            && digit(chars[offset + 6]) && digit(chars[offset + 7]);
                    if (valid) second = number(chars[offset + 6], chars[offset + 7]);
                }
                if (valid && timeEnd > 8) {
                    valid = chars[offset + 8] == '.';
                    for (int i = 9; valid && i < timeEnd; i++) {
                        char c = chars[offset + i];
                        valid = digit(c);
                        if (valid) nano = nano * 10 + c - '0';
                    }
                    if (valid) nano *= NANO_SCALE[18 - timeEnd];
                }
                int offsetSeconds = 0;
                if (valid && numericOffset) {
                    char hh0 = chars[offset + total - 5];
                    char hh1 = chars[offset + total - 4];
                    char mm0 = chars[offset + total - 2];
                    char mm1 = chars[offset + total - 1];
                    valid = digit(hh0) && digit(hh1) && digit(mm0) && digit(mm1);
                    if (valid) {
                        int zoneHour = number(hh0, hh1);
                        int zoneMinute = number(mm0, mm1);
                        valid = zoneHour <= 18 && zoneMinute <= 59
                                && (zoneHour < 18 || zoneMinute == 0);
                        if (valid) {
                            offsetSeconds = (zoneHour * 60 + zoneMinute) * 60;
                            if (chars[offset + total - 6] == '-') offsetSeconds = -offsetSeconds;
                        }
                    }
                }
                if (valid) {
                    try {
                        return OffsetTime.of(hour, minute, second, nano,
                                ZoneOffset.ofTotalSeconds(offsetSeconds));
                    } catch (DateTimeException ignored) {
                        // Preserve JDK exception behavior for malformed values.
                    }
                }
            }
        }
        return OffsetTime.parse(new String(chars, offset, length));
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int number(char tens, char ones) {
        return (tens - '0') * 10 + ones - '0';
    }
}
