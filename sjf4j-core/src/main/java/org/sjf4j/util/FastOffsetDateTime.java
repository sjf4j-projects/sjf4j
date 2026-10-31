package org.sjf4j.util;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Fast canonical ISO offset date-time decoding without a DateTimeFormatter:
 * yyyy-MM-dd'T'HH:mm[:ss[.fraction]] followed by Z or +/-HH:mm.
 *
 * Noncanonical representations, unsupported offset spellings and malformed
 * values delegate to OffsetDateTime.parse for precise JDK semantics.
 */
public final class FastOffsetDateTime {

    private static final int[] NANO_SCALE = {
            1, 10, 100, 1_000, 10_000, 100_000,
            1_000_000, 10_000_000, 100_000_000
    };

    private FastOffsetDateTime() {
    }

    public static OffsetDateTime parse(String text) {
        if (text != null) {
            final int total = text.length();
            final boolean utc = total > 0 && text.charAt(total - 1) == 'Z';
            final boolean numericOffset = total >= 6
                    && (text.charAt(total - 6) == '+' || text.charAt(total - 6) == '-')
                    && text.charAt(total - 3) == ':';
            final int timeEnd = utc ? total - 1 : total - 6;
            if ((utc || numericOffset)
                    && (timeEnd == 16 || timeEnd == 19 || (timeEnd >= 21 && timeEnd <= 29))
                    && text.charAt(4) == '-' && text.charAt(7) == '-'
                    && text.charAt(10) == 'T' && text.charAt(13) == ':'
                    && digit(text.charAt(0)) && digit(text.charAt(1))
                    && digit(text.charAt(2)) && digit(text.charAt(3))
                    && digit(text.charAt(5)) && digit(text.charAt(6))
                    && digit(text.charAt(8)) && digit(text.charAt(9))
                    && digit(text.charAt(11)) && digit(text.charAt(12))
                    && digit(text.charAt(14)) && digit(text.charAt(15))) {
                int second = 0;
                int nano = 0;
                boolean valid = true;
                if (timeEnd > 16) {
                    valid = text.charAt(16) == ':'
                            && digit(text.charAt(17)) && digit(text.charAt(18));
                    if (valid) second = number(text.charAt(17), text.charAt(18));
                }
                if (valid && timeEnd > 19) {
                    valid = text.charAt(19) == '.';
                    for (int i = 20; valid && i < timeEnd; i++) {
                        char c = text.charAt(i);
                        valid = digit(c);
                        if (valid) nano = nano * 10 + c - '0';
                    }
                    if (valid) nano *= NANO_SCALE[29 - timeEnd];
                }
                int offsetSeconds = 0;
                if (valid && numericOffset) {
                    int offsetHour = number(text.charAt(total - 5), text.charAt(total - 4));
                    int offsetMinute = number(text.charAt(total - 2), text.charAt(total - 1));
                    valid = digit(text.charAt(total - 5)) && digit(text.charAt(total - 4))
                            && digit(text.charAt(total - 2)) && digit(text.charAt(total - 1))
                            && offsetHour <= 18 && offsetMinute < 60
                            && (offsetHour != 18 || offsetMinute == 0);
                    if (valid) {
                        offsetSeconds = (offsetHour * 60 + offsetMinute) * 60;
                        if (text.charAt(total - 6) == '-') offsetSeconds = -offsetSeconds;
                    }
                }
                if (valid) {
                    int year = (text.charAt(0) - '0') * 1000
                            + (text.charAt(1) - '0') * 100
                            + (text.charAt(2) - '0') * 10 + text.charAt(3) - '0';
                    try {
                        return OffsetDateTime.of(year,
                                number(text.charAt(5), text.charAt(6)),
                                number(text.charAt(8), text.charAt(9)),
                                number(text.charAt(11), text.charAt(12)),
                                number(text.charAt(14), text.charAt(15)),
                                second, nano, ZoneOffset.ofTotalSeconds(offsetSeconds));
                    } catch (DateTimeException ignored) {
                        // JDK parse retains its standard error behavior and any
                        // noncanonical forms that it accepts.
                    }
                }
            }
        }
        return OffsetDateTime.parse(text);
    }

    /** Parses canonical text directly from a borrowed character array. */
    public static OffsetDateTime parse(char[] chars, int offset, int length) {
        if (chars != null && offset >= 0 && length >= 0 && offset <= chars.length - length) {
            final int total = length;
            final boolean utc = total > 0 && chars[offset + total - 1] == 'Z';
            final boolean numericOffset = total >= 6
                    && (chars[offset + total - 6] == '+' || chars[offset + total - 6] == '-')
                    && chars[offset + total - 3] == ':';
            final int timeEnd = utc ? total - 1 : total - 6;
            if ((utc || numericOffset)
                    && (timeEnd == 16 || timeEnd == 19 || (timeEnd >= 21 && timeEnd <= 29))
                    && chars[offset + 4] == '-' && chars[offset + 7] == '-'
                    && chars[offset + 10] == 'T' && chars[offset + 13] == ':'
                    && digit(chars[offset + 0]) && digit(chars[offset + 1])
                    && digit(chars[offset + 2]) && digit(chars[offset + 3])
                    && digit(chars[offset + 5]) && digit(chars[offset + 6])
                    && digit(chars[offset + 8]) && digit(chars[offset + 9])
                    && digit(chars[offset + 11]) && digit(chars[offset + 12])
                    && digit(chars[offset + 14]) && digit(chars[offset + 15])) {
                int second = 0;
                int nano = 0;
                boolean valid = true;
                if (timeEnd > 16) {
                    valid = chars[offset + 16] == ':'
                            && digit(chars[offset + 17]) && digit(chars[offset + 18]);
                    if (valid) second = number(chars[offset + 17], chars[offset + 18]);
                }
                if (valid && timeEnd > 19) {
                    valid = chars[offset + 19] == '.';
                    for (int i = 20; valid && i < timeEnd; i++) {
                        char c = chars[offset + i];
                        valid = digit(c);
                        if (valid) nano = nano * 10 + c - '0';
                    }
                    if (valid) nano *= NANO_SCALE[29 - timeEnd];
                }
                int offsetSeconds = 0;
                if (valid && numericOffset) {
                    int offsetHour = number(chars[offset + total - 5], chars[offset + total - 4]);
                    int offsetMinute = number(chars[offset + total - 2], chars[offset + total - 1]);
                    valid = digit(chars[offset + total - 5]) && digit(chars[offset + total - 4])
                            && digit(chars[offset + total - 2]) && digit(chars[offset + total - 1])
                            && offsetHour <= 18 && offsetMinute < 60
                            && (offsetHour != 18 || offsetMinute == 0);
                    if (valid) {
                        offsetSeconds = (offsetHour * 60 + offsetMinute) * 60;
                        if (chars[offset + total - 6] == '-') offsetSeconds = -offsetSeconds;
                    }
                }
                if (valid) {
                    int year = (chars[offset + 0] - '0') * 1000
                            + (chars[offset + 1] - '0') * 100
                            + (chars[offset + 2] - '0') * 10 + chars[offset + 3] - '0';
                    try {
                        return OffsetDateTime.of(year,
                                number(chars[offset + 5], chars[offset + 6]),
                                number(chars[offset + 8], chars[offset + 9]),
                                number(chars[offset + 11], chars[offset + 12]),
                                number(chars[offset + 14], chars[offset + 15]),
                                second, nano, ZoneOffset.ofTotalSeconds(offsetSeconds));
                    } catch (DateTimeException ignored) {
                        // JDK parse retains its standard error behavior and any
                        // noncanonical forms that it accepts.
                    }
                }
            }
        }
        return OffsetDateTime.parse(new String(chars, offset, length));
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int number(char tens, char ones) {
        return (tens - '0') * 10 + ones - '0';
    }
}
