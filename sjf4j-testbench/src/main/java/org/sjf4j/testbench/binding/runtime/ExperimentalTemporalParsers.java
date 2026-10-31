package org.sjf4j.testbench.binding.runtime;

import java.time.Duration;
import java.time.Period;
import java.util.Objects;

/**
 * Benchmark-only, allocation-conscious candidates for the JDK ISO parsers.
 *
 * <p>This class is deliberately located in the testbench module. It is not a
 * production API and must not be promoted without benchmark and compatibility
 * evidence.</p>
 */
public final class ExperimentalTemporalParsers {

    private static final int[] NANO_SCALE = {
            1, 10, 100, 1_000, 10_000, 100_000,
            1_000_000, 10_000_000, 100_000_000
    };

    private ExperimentalTemporalParsers() {
    }

    public static Duration parseDuration(String text) {
        Objects.requireNonNull(text, "text");
        Duration parsed = tryParseDuration(text);
        return parsed != null ? parsed : Duration.parse(text);
    }

    public static Period parsePeriod(String text) {
        Objects.requireNonNull(text, "text");
        Period parsed = tryParsePeriod(text);
        return parsed != null ? parsed : Period.parse(text);
    }

    private static Duration tryParseDuration(String text) {
        int length = text.length();
        if (length < 2) return null;

        int index = 0;
        boolean negate = false;
        char first = text.charAt(index);
        if (first == '-' || first == '+') {
            negate = first == '-';
            index++;
        }
        if (index >= length || upper(text.charAt(index++)) != 'P') return null;

        long days = 0;
        long hours = 0;
        long minutes = 0;
        long seconds = 0;
        int nanos = 0;
        boolean hasDay = false;

        // The only date-based component supported by Duration is days.
        if (index < length && numberStart(text.charAt(index))) {
            int start = index;
            int end = numberEnd(text, start);
            if (end < 0 || end >= length || upper(text.charAt(end)) != 'D') return null;
            long value = parseLong(text, start, end);
            if (value == Long.MIN_VALUE) return null;
            try {
                days = Math.multiplyExact(value, 86_400L);
            } catch (ArithmeticException overflow) {
                return null;
            }
            index = end + 1;
            hasDay = true;
        }

        if (index < length && upper(text.charAt(index)) == 'T') {
            index++;
            int previousUnit = 0;
            boolean hasTime = false;

            while (index < length && numberStart(text.charAt(index))) {
                int start = index;
                int end = numberEnd(text, start);
                if (end < 0 || end >= length) return null;

                long value = parseLong(text, start, end);
                if (value == Long.MIN_VALUE) return null;

                char suffix = text.charAt(end);
                int suffixIndex = end;
                int fraction = 0;
                boolean hasFraction = suffix == '.' || suffix == ',';
                if (hasFraction) {
                    int fractionStart = end + 1;
                    int fractionEnd = fractionStart;
                    while (fractionEnd < length && digit(text.charAt(fractionEnd))) {
                        fractionEnd++;
                    }
                    int fractionLength = fractionEnd - fractionStart;
                    if (fractionLength > 9 || fractionEnd >= length
                            || upper(text.charAt(fractionEnd)) != 'S') {
                        return null;
                    }
                    fraction = parseFraction(text, fractionStart, fractionEnd,
                            text.charAt(start) == '-');
                    suffixIndex = fractionEnd;
                    suffix = 'S';
                } else {
                    suffix = upper(suffix);
                }

                try {
                    if (suffix == 'H' && previousUnit < 1) {
                        hours = Math.multiplyExact(value, 3_600L);
                        previousUnit = 1;
                        index = end + 1;
                    } else if (suffix == 'M' && previousUnit < 2) {
                        minutes = Math.multiplyExact(value, 60L);
                        previousUnit = 2;
                        index = end + 1;
                    } else if (suffix == 'S' && previousUnit < 3) {
                        seconds = value;
                        nanos = hasFraction ? fraction : 0;
                        previousUnit = 3;
                        index = suffixIndex + 1;
                    } else {
                        return null;
                    }
                } catch (ArithmeticException overflow) {
                    return null;
                }

                hasTime = true;
                if (previousUnit == 3) break;
            }

            if (!hasTime || index != length) return null;
        } else if (!hasDay || index != length) {
            return null;
        }

        try {
            // Match Duration.parse's component summation order and sign rules.
            long totalSeconds = Math.addExact(days,
                    Math.addExact(hours, Math.addExact(minutes, seconds)));
            Duration result = Duration.ofSeconds(totalSeconds, nanos);
            return negate ? result.negated() : result;
        } catch (ArithmeticException overflow) {
            // Let the JDK produce its established overflow exception.
            return null;
        }
    }

    private static Period tryParsePeriod(String text) {
        int length = text.length();
        if (length < 2) return null;

        int index = 0;
        boolean negate = false;
        char first = text.charAt(index);
        if (first == '-' || first == '+') {
            negate = first == '-';
            index++;
        }
        if (index >= length || upper(text.charAt(index++)) != 'P') return null;

        int years = 0;
        int months = 0;
        int weeks = 0;
        int days = 0;
        int previousUnit = 0;
        boolean found = false;

        while (index < length) {
            int start = index;
            int end = numberEnd(text, start);
            if (end < 0 || end >= length) return null;

            long parsed = parseLong(text, start, end);
            if (parsed == Long.MIN_VALUE || parsed < Integer.MIN_VALUE
                    || parsed > Integer.MAX_VALUE) {
                return null;
            }

            int value = (int) parsed;
            if (negate) {
                try {
                    value = Math.negateExact(value);
                } catch (ArithmeticException overflow) {
                    return null;
                }
            }

            int unit;
            switch (upper(text.charAt(end))) {
                case 'Y': unit = 1; years = value; break;
                case 'M': unit = 2; months = value; break;
                case 'W': unit = 3; weeks = value; break;
                case 'D': unit = 4; days = value; break;
                default: return null;
            }
            if (unit <= previousUnit) return null;
            previousUnit = unit;
            found = true;
            index = end + 1;
        }

        if (!found) return null;

        try {
            days = Math.addExact(days, Math.multiplyExact(weeks, 7));
            return Period.of(years, months, days);
        } catch (ArithmeticException overflow) {
            // Let Period.parse produce the established overflow exception.
            return null;
        }
    }

    private static int numberEnd(String text, int start) {
        int index = start;
        if (index < text.length()) {
            char c = text.charAt(index);
            if (c == '-' || c == '+') index++;
        }
        int digits = index;
        while (index < text.length() && digit(text.charAt(index))) {
            index++;
        }
        return index == digits ? -1 : index;
    }

    /**
     * Negative accumulation follows the overflow-safe strategy used by the
     * JDK integer parsers. Long.MIN_VALUE is used as a fallback sentinel;
     * that single valid edge value intentionally takes the JDK path too.
     */
    private static long parseLong(String text, int start, int end) {
        int index = start;
        boolean negative = text.charAt(index) == '-';
        if (text.charAt(index) == '-' || text.charAt(index) == '+') index++;

        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long multiplyLimit = limit / 10;
        long result = 0;

        while (index < end) {
            int value = text.charAt(index++) - '0';
            if (result < multiplyLimit) return Long.MIN_VALUE;
            result *= 10;
            if (result < limit + value) return Long.MIN_VALUE;
            result -= value;
        }
        return negative ? result : -result;
    }

    private static int parseFraction(String text, int start, int end, boolean negative) {
        if (start == end) return 0;
        int fraction = 0;
        for (int i = start; i < end; i++) {
            fraction = fraction * 10 + text.charAt(i) - '0';
        }
        fraction *= NANO_SCALE[9 - (end - start)];
        return negative ? -fraction : fraction;
    }

    private static boolean numberStart(char c) {
        return digit(c) || c == '-' || c == '+';
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    private static char upper(char c) {
        return c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c;
    }
}
