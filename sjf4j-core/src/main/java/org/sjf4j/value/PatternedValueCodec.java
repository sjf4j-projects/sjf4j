package org.sjf4j.value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Optional interface for {@link ValueCodec}s that support date/time format patterns.
 * <p>
 * A codec implementing this interface can be parameterized with a
 * {@link DateTimeFormatter} pattern (e.g. {@code "yyyy-MM-dd"}) via the
 * {@link #withPattern(String)} method. The returned codec instance applies the
 * pattern during {@link ValueCodec#valueToRaw(Object)} encoding and
 * {@link ValueCodec#rawToValue(Object)} decoding.
 * <p>
 * Implementations must be deterministic: repeated calls with the same pattern
 * must return behaviorally equivalent (though possibly different) instances.
 */
public interface PatternedValueCodec<V, R> extends ValueCodec<V, R> {

    /**
     * Returns a new {@link ValueCodec} configured with the given format pattern.
     * <p>
     * The pattern follows {@link DateTimeFormatter} conventions.
     *
     * @param pattern the format pattern, never null and must be a valid pattern
     * @return a codec instance using the given pattern
     */
    ValueCodec<V, R> withPattern(String pattern);


    // ──────────────────────────────────────────────────────────────
    //  Temporal codec implementation
    // ──────────────────────────────────────────────────────────────

    /**
     * Generic codec for temporal types (LocalDate, LocalTime, etc.) with
     * optional DateTimeFormatter pattern support.
     */
    final class TemporalValueCodec<V> extends ValueCodec.StreamingValueCodec<V, String>
            implements PatternedValueCodec<V, String> {
        private final Class<V> type;
        private final DateTimeFormatter formatter;
        private final Function<V, String> toString;
        private final BiFunction<V, DateTimeFormatter, String> format;
        private final Function<String, V> parse;
        private final BiFunction<String, DateTimeFormatter, V> parseWith;

        TemporalValueCodec(Class<V> type, DateTimeFormatter formatter,
                                   Function<V, String> toString,
                                   BiFunction<V, DateTimeFormatter, String> format,
                                   Function<String, V> parse,
                                   BiFunction<String, DateTimeFormatter, V> parseWith,
                                   Parser<V> streamParser, Generator<V> streamGenerator) {
            super(type, String.class, toString, parse, streamParser, streamGenerator);
            this.type = type;
            this.formatter = formatter;
            this.toString = toString;
            this.format = format;
            this.parse = parse;
            this.parseWith = parseWith;
        }

        @Override
        public String valueToRaw(V value) {
            if (value == null) return null;
            return formatter != null ? format.apply(value, formatter) : toString.apply(value);
        }

        @Override
        public V rawToValue(String raw) {
            if (raw == null) return null;
            return formatter != null ? parseWith.apply(raw, formatter) : parse.apply(raw);
        }

        @Override
        public Class<V> valueClazz() {
            return type;
        }
        @Override
        public Class<String> rawClazz() {
            return String.class;
        }

        @Override
        public ValueCodec<V, String> withPattern(String pattern) {
            // Custom formats use a precompiled DateTimeFormatter rather than
            // streaming ISO functions; no per-value pattern checks.
            return new TemporalValueCodec<>(type, DateTimeFormatter.ofPattern(pattern),
                    toString, format, parse, parseWith, null, null);
        }
    }


    // ──────────────────────────────────────────────────────────────
    //  Historical aliases to the built-in temporal codec instances
    // ──────────────────────────────────────────────────────────────

    PatternedValueCodec<LocalDate, String> LOCAL_DATE =
            (PatternedValueCodec<LocalDate, String>) ValueCodec.LOCAL_DATE;

    PatternedValueCodec<LocalTime, String> LOCAL_TIME =
            (PatternedValueCodec<LocalTime, String>) ValueCodec.LOCAL_TIME;

    PatternedValueCodec<LocalDateTime, String> LOCAL_DATE_TIME =
            (PatternedValueCodec<LocalDateTime, String>) ValueCodec.LOCAL_DATE_TIME;

    PatternedValueCodec<OffsetDateTime, String> OFFSET_DATE_TIME =
            (PatternedValueCodec<OffsetDateTime, String>) ValueCodec.OFFSET_DATE_TIME;

    PatternedValueCodec<ZonedDateTime, String> ZONED_DATE_TIME =
            (PatternedValueCodec<ZonedDateTime, String>) ValueCodec.ZONED_DATE_TIME;

    PatternedValueCodec<OffsetTime, String> OFFSET_TIME =
            (PatternedValueCodec<OffsetTime, String>) ValueCodec.OFFSET_TIME;

}
