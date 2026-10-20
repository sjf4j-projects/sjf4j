package org.sjf4j.value;

import org.sjf4j.exception.BindingException;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.util.FastUuid;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Codec contract for mapping domain value types to raw OBNT representations.
 * <p>
 * This is the extension point behind {@code @NodeValue}. A codec lets a custom
 * Java type behave like one logical value node in SJF4J instead of being
 * analyzed as a POJO.
 *
 * <p>The codec owns the raw representation returned by {@link #valueToRaw(Object)}
 * and accepted by {@link #rawToValue(Object)}. It should use SJF4J-supported
 * fixed OBNT forms such as
 * {@link String}, {@link Number}, {@link Boolean}, {@link java.util.Map},
 * {@link java.util.List}. {@link Object} is not a supported declared raw
 * type. A codec may return {@code null} from {@link #valueToRaw(Object)} to
 * write a JSON null, and {@link #rawToValue(Object)} receives {@code null} for
 * a JSON null. Null is not itself a raw class. The framework does not recursively
 * bind or copy a codec raw value at this boundary.
 * <p>
 * A map-shaped raw value should be handled as {@code Map<String, Object>}. Its
 * contents remain the codec's responsibility; SJF4J does not recursively bind
 * typed map values such as {@code Map<String, SomePojo>} while decoding a codec
 * raw value. This is a contract rather than a
 * registration-time check because generic type arguments are erased from
 * {@link #rawClazz()}.
 */
public interface ValueCodec<V, R> {

    /**
     * Encodes a domain value to the raw representation consumed by a binder or schema.
     * May return {@code null} to write a JSON null.
     */
    R valueToRaw(V value);

    /**
     * Decodes a raw representation back to the domain value.
     * JSON null is passed as {@code null}.
     */
    V rawToValue(R raw);

    /**
     * Returns the domain value type handled by this codec.
     */
    Class<V> valueClazz();

    /**
     * Returns the fixed raw node type produced and consumed by this codec.
     * This must not be {@link Object}; a null raw value is represented by a
     * null result from {@link #valueToRaw(Object)}, not by a raw class.
     */
    Class<R> rawClazz();

    /**
     * Returns a copy of the value when custom copy semantics are needed.
     * <p>
     * Override this for mutable value types. The default implementation returns
     * the input reference unchanged; it does not encode and decode the value.
     */
    default V valueCopy(V value) {
        return value;
    }


    // ──────────────────────────────────────────────────────────────
    //  Generic codec implementations
    // ──────────────────────────────────────────────────────────────

    /**
     * Simple codec for a (V, R) pair with encoder/decoder functions. Null
     * input is passed through without invoking either function; the optional
     * copier defaults to the identity function.
     */
    final class SimpleValueCodec<V, R> implements ValueCodec<V, R> {
        private final Class<V> valueClazz;
        private final Class<R> rawClazz;
        private final Function<V, R> encoder;
        private final Function<R, V> decoder;
        private final Function<V, V> copier;

        public SimpleValueCodec(Class<V> valueClazz, Class<R> rawClazz,
                                Function<V, R> encoder, Function<R, V> decoder) {
            this(valueClazz, rawClazz, encoder, decoder, Function.identity());
        }

        public SimpleValueCodec(Class<V> valueClazz, Class<R> rawClazz,
                                Function<V, R> encoder, Function<R, V> decoder,
                                Function<V, V> copier) {
            this.valueClazz = valueClazz;
            this.rawClazz = rawClazz;
            this.encoder = encoder;
            this.decoder = decoder;
            this.copier = copier;
        }

        @Override
        public R valueToRaw(V value) {
            return value == null ? null : encoder.apply(value);
        }
        @Override
        public V rawToValue(R raw) {
            return raw == null ? null : decoder.apply(raw);
        }
        @Override
        public Class<V> valueClazz() {
            return valueClazz;
        }
        @Override
        public Class<R> rawClazz() {
            return rawClazz;
        }
        @Override
        public V valueCopy(V value) {
            return copier.apply(value);
        }
    }

    /**
     * A regular ValueCodec with optional direct streaming paths.
     * A missing direction retains the existing typed raw ValueCodec path.
     * A streaming function consumes or writes exactly one JSON value,
     * including JSON null.
     */
    final class StreamingValueCodec<V, R> implements ValueCodec<V, R> {
        @FunctionalInterface
        public interface Reader<V> {
            V read(StreamingReader reader) throws IOException;
        }

        @FunctionalInterface
        public interface Writer<V> {
            void write(StreamingWriter writer, V value) throws IOException;
        }

        private final Class<V> valueClazz;
        private final Class<R> rawClazz;
        private final Function<V, R> encoder;
        private final Function<R, V> decoder;
        private final Function<V, V> copier;
        private final Reader<V> streamReader;
        private final Writer<V> streamWriter;

        public StreamingValueCodec(Class<V> valueClazz, Class<R> rawClazz,
                                   Function<V, R> encoder, Function<R, V> decoder,
                                   Reader<V> streamReader, Writer<V> streamWriter) {
            this(valueClazz, rawClazz, encoder, decoder, Function.identity(), streamReader, streamWriter);
        }

        public StreamingValueCodec(Class<V> valueClazz, Class<R> rawClazz,
                                   Function<V, R> encoder, Function<R, V> decoder,
                                   Function<V, V> copier, Reader<V> streamReader, Writer<V> streamWriter) {
            this.valueClazz = valueClazz;
            this.rawClazz = rawClazz;
            this.encoder = encoder;
            this.decoder = decoder;
            this.copier = copier;
            this.streamReader = streamReader;
            this.streamWriter = streamWriter;
        }

        @Override
        public R valueToRaw(V value) {
            return value == null ? null : encoder.apply(value);
        }

        @Override
        public V rawToValue(R raw) {
            return raw == null ? null : decoder.apply(raw);
        }

        @Override
        public Class<V> valueClazz() { return valueClazz; }

        @Override
        public Class<R> rawClazz() { return rawClazz; }

        @Override
        public V valueCopy(V value) { return copier.apply(value); }

        public Reader<V> streamReader() { return streamReader; }

        public Writer<V> streamWriter() { return streamWriter; }
    }

    // ──────────────────────────────────────────────────────────────
    //  Built-in codec instances
    // ──────────────────────────────────────────────────────────────

    @NodeValue
    ValueCodec<URI, String> URI_CODEC = new SimpleValueCodec<>(URI.class, String.class,
            URI::toString, URI::create);

    @NodeValue
    ValueCodec<URL, String> URL_CODEC = new SimpleValueCodec<>(URL.class, String.class,
            URL::toString, raw -> {
                try {
                    return URI.create(raw).toURL();
                } catch (IllegalArgumentException | MalformedURLException e) {
                    throw new BindingException("invalid URL '" + raw + "'", e);
                }
            });

    @NodeValue(format = "canonical", defaultCodec = true)
    ValueCodec<UUID, String> UUID_CODEC = new StreamingValueCodec<>(UUID.class, String.class,
            FastUuid::toString, FastUuid::parseUUID,
            StreamingReader::readUUID, StreamingWriter::writeUUID);

    @NodeValue
    ValueCodec<Charset, String> CHARSET = new SimpleValueCodec<>(Charset.class, String.class,
            Charset::name, Charset::forName);

    @NodeValue
    ValueCodec<Locale, String> LOCALE = new SimpleValueCodec<>(Locale.class, String.class,
            Locale::toLanguageTag, Locale::forLanguageTag);

    @NodeValue
    ValueCodec<Currency, String> CURRENCY = new SimpleValueCodec<>(Currency.class, String.class,
            Currency::getCurrencyCode, Currency::getInstance);

    @NodeValue
    ValueCodec<ZoneId, String> ZONE_ID = new SimpleValueCodec<>(ZoneId.class, String.class,
            ZoneId::getId, ZoneId::of);

    @NodeValue(format = "iso", defaultCodec = true)
    ValueCodec<Instant, String> INSTANT_STR = new SimpleValueCodec<>(Instant.class, String.class,
            Instant::toString, Instant::parse);

    @NodeValue(format = "epochMillis")
    ValueCodec<Instant, Long> INSTANT_EPOCH_MILLIS = new SimpleValueCodec<>(Instant.class, Long.class,
            Instant::toEpochMilli, Instant::ofEpochMilli);

    @NodeValue
    ValueCodec<Duration, String> DURATION = new SimpleValueCodec<>(Duration.class, String.class,
            Duration::toString, Duration::parse);

    @NodeValue
    ValueCodec<Period, String> PERIOD = new SimpleValueCodec<>(Period.class, String.class,
            Period::toString, Period::parse);

    @NodeValue
    ValueCodec<Path, String> PATH = new SimpleValueCodec<>(Path.class, String.class,
            Path::toString, Paths::get);

    @NodeValue
    ValueCodec<File, String> FILE = new SimpleValueCodec<>(File.class, String.class,
            File::toString, File::new);

    @NodeValue
    ValueCodec<Pattern, String> PATTERN = new SimpleValueCodec<>(Pattern.class, String.class,
            Pattern::pattern, Pattern::compile);

    @NodeValue
    ValueCodec<InetAddress, String> INET_ADDR = new SimpleValueCodec<>(InetAddress.class, String.class,
            InetAddress::getHostAddress, raw -> {
                try { return InetAddress.getByName(raw); }
                catch (UnknownHostException e) { throw new BindingException("invalid InetAddress '" + raw + "'", e); }
            });

    @NodeValue
    ValueCodec<Date, String> DATE = new SimpleValueCodec<>(Date.class, String.class,
            v -> v.toInstant().toString(), raw -> Date.from(Instant.parse(raw)));

    // Retain the historical PatternedValueCodec fields as aliases; register
    // all built-in value types from this single declaration surface.
    @NodeValue
    ValueCodec<LocalDate, String> LOCAL_DATE = PatternedValueCodec.LOCAL_DATE;

    @NodeValue
    ValueCodec<LocalTime, String> LOCAL_TIME = PatternedValueCodec.LOCAL_TIME;

    @NodeValue
    ValueCodec<LocalDateTime, String> LOCAL_DATE_TIME = PatternedValueCodec.LOCAL_DATE_TIME;

    @NodeValue
    ValueCodec<OffsetDateTime, String> OFFSET_DATE_TIME = PatternedValueCodec.OFFSET_DATE_TIME;

    @NodeValue
    ValueCodec<ZonedDateTime, String> ZONED_DATE_TIME = PatternedValueCodec.ZONED_DATE_TIME;

    // ──────────────────────────────────────────────────────────────
    //  Complex codecs → private static methods + method reference
    // ──────────────────────────────────────────────────────────────

    @NodeValue
    ValueCodec<Calendar, String> CALENDAR = new SimpleValueCodec<>(Calendar.class, String.class,
            ValueCodec::_calendarToRaw, ValueCodec::_calendarFromRaw);

    // calendar → string
    static String _calendarToRaw(Calendar value) {
        ZoneId zoneId = value.getTimeZone().toZoneId();
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(value.toInstant(), zoneId);
        return zonedDateTime.toString();
    }

    // string → calendar
    static Calendar _calendarFromRaw(String raw) {
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(raw);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(zonedDateTime.getZone()));
        calendar.setTimeInMillis(zonedDateTime.toInstant().toEpochMilli());
        return calendar;
    }

}
