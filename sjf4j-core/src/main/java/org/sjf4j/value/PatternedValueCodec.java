package org.sjf4j.value;

import org.sjf4j.binding.StreamingParser;
import org.sjf4j.binding.StreamingGenerator;
import org.sjf4j.util.FastLocalTime;
import org.sjf4j.util.FastOffsetTime;
import org.sjf4j.util.FastLocalDate;
import org.sjf4j.util.FastOffsetDateTime;
import org.sjf4j.util.FastLocalDateTime;

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

        private TemporalValueCodec(Class<V> type, DateTimeFormatter formatter,
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
    //  Built-in temporal codec instances
    // ──────────────────────────────────────────────────────────────

    PatternedValueCodec<LocalDate, String> LOCAL_DATE = new TemporalValueCodec<>(LocalDate.class,
            null, LocalDate::toString, LocalDate::format,
            FastLocalDate::parse, LocalDate::parse,
            StreamingParser::readLocalDate, StreamingGenerator::writeLocalDate);

    PatternedValueCodec<LocalTime, String> LOCAL_TIME = new TemporalValueCodec<>(LocalTime.class,
            null, LocalTime::toString, LocalTime::format,
            FastLocalTime::parse, LocalTime::parse,
            StreamingParser::readLocalTime, StreamingGenerator::writeLocalTime);

    PatternedValueCodec<LocalDateTime, String> LOCAL_DATE_TIME = new TemporalValueCodec<>(LocalDateTime.class,
            null, LocalDateTime::toString, LocalDateTime::format,
            FastLocalDateTime::parse, LocalDateTime::parse,
            StreamingParser::readLocalDateTime, StreamingGenerator::writeLocalDateTime);

    PatternedValueCodec<OffsetDateTime, String> OFFSET_DATE_TIME = new TemporalValueCodec<>(OffsetDateTime.class,
            null, OffsetDateTime::toString, OffsetDateTime::format,
            FastOffsetDateTime::parse, OffsetDateTime::parse,
            StreamingParser::readOffsetDateTime, StreamingGenerator::writeOffsetDateTime);

    PatternedValueCodec<ZonedDateTime, String> ZONED_DATE_TIME = new TemporalValueCodec<>(ZonedDateTime.class,
            null, ZonedDateTime::toString, ZonedDateTime::format,
            ZonedDateTime::parse, ZonedDateTime::parse,
            StreamingParser::readZonedDateTime, StreamingGenerator::writeZonedDateTime);

    PatternedValueCodec<OffsetTime, String> OFFSET_TIME = new TemporalValueCodec<>(OffsetTime.class,
            null, OffsetTime::toString, OffsetTime::format,
            FastOffsetTime::parse, OffsetTime::parse,
            StreamingParser::readOffsetTime, StreamingGenerator::writeOffsetTime);

}
