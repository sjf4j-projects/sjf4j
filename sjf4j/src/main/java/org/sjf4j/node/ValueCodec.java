package org.sjf4j.node;

import org.sjf4j.exception.JsonException;

import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Codec contract for mapping domain value types to raw OBNT node values.
 * <p>
 * This is the extension point behind {@code @NodeValue}. A codec lets a custom
 * Java type behave like a single logical JSON value in SJF4J instead of being
 * analyzed as a POJO.
 *
 * <p>The raw side should use SJF4J-supported node forms such as
 * {@link String}, {@link Number}, {@link Boolean}, {@link java.util.Map},
 * {@link java.util.List}, or {@link Object}. Implementations should be stable,
 * deterministic, and reversible for predictable reads and writes.
 */
public interface ValueCodec<V, R> {

    /**
     * Encodes a domain value to its raw node representation.
     */
    R valueToRaw(V value);

    /**
     * Decodes a raw node representation back to the domain value.
     */
    V rawToValue(R raw);

    /**
     * Returns the domain value type handled by this codec.
     */
    Class<V> valueClass();

    /**
     * Returns the raw node type produced and consumed by this codec.
     */
    Class<R> rawClass();

    /**
     * Returns a copy of the value when custom copy semantics are needed.
     * <p>
     * Override this for mutable value types. The default implementation returns
     * the input reference unchanged.
     */
    default V valueCopy(V value) {return value;}


    // ──────────────────────────────────────────────────────────────
    //  Generic codec implementations
    // ──────────────────────────────────────────────────────────────

    /**
     * Simple codec for any (V, R) pair with encoder/decoder functions.
     */
    final class SimpleValueCodec<V, R> implements ValueCodec<V, R> {
        private final Class<V> valueType;
        private final Class<R> rawType;
        private final Function<V, R> encoder;
        private final Function<R, V> decoder;
        private final Function<V, V> copier;

        public SimpleValueCodec(Class<V> valueType, Class<R> rawType,
                                Function<V, R> encoder, Function<R, V> decoder) {
            this(valueType, rawType, encoder, decoder, Function.identity());
        }

        public SimpleValueCodec(Class<V> valueType, Class<R> rawType,
                                Function<V, R> encoder, Function<R, V> decoder,
                                Function<V, V> copier) {
            this.valueType = valueType;
            this.rawType = rawType;
            this.encoder = encoder;
            this.decoder = decoder;
            this.copier = copier;
        }

        @Override public R valueToRaw(V value) { return value == null ? null : encoder.apply(value); }
        @Override public V rawToValue(R raw)   { return raw == null ? null : decoder.apply(raw); }
        @Override public Class<V> valueClass() { return valueType; }
        @Override public Class<R> rawClass()   { return rawType; }
        @Override public V valueCopy(V value)  { return copier.apply(value); }
    }

    // ──────────────────────────────────────────────────────────────
    //  Built-in codec instances
    // ──────────────────────────────────────────────────────────────

    ValueCodec<URI, String> URI_CODEC = new SimpleValueCodec<>(URI.class, String.class,
            URI::toString, URI::create);

    ValueCodec<URL, String> URL_CODEC = new SimpleValueCodec<>(URL.class, String.class,
            URL::toString, raw -> {
                try { return new URL(raw); }
                catch (MalformedURLException e) { throw new JsonException("invalid URL: " + raw, e); }
            });

    ValueCodec<UUID, String> UUID_CODEC = new SimpleValueCodec<>(UUID.class, String.class,
            UUID::toString, UUID::fromString);

    ValueCodec<Locale, String> LOCALE = new SimpleValueCodec<>(Locale.class, String.class,
            Locale::toLanguageTag, Locale::forLanguageTag);

    ValueCodec<Currency, String> CURRENCY = new SimpleValueCodec<>(Currency.class, String.class,
            Currency::getCurrencyCode, Currency::getInstance);

    ValueCodec<ZoneId, String> ZONE_ID = new SimpleValueCodec<>(ZoneId.class, String.class,
            ZoneId::getId, ZoneId::of);

    ValueCodec<Instant, String> INSTANT_STR = new SimpleValueCodec<>(Instant.class, String.class,
            Instant::toString, Instant::parse);

    ValueCodec<Instant, Long> INSTANT_EPOCH_MILLIS = new SimpleValueCodec<>(Instant.class, Long.class,
            Instant::toEpochMilli, Instant::ofEpochMilli);

    ValueCodec<Duration, String> DURATION = new SimpleValueCodec<>(Duration.class, String.class,
            Duration::toString, Duration::parse);

    ValueCodec<Period, String> PERIOD = new SimpleValueCodec<>(Period.class, String.class,
            Period::toString, Period::parse);

    ValueCodec<Path, String> PATH = new SimpleValueCodec<>(Path.class, String.class,
            Path::toString, Paths::get);

    ValueCodec<File, String> FILE = new SimpleValueCodec<>(File.class, String.class,
            File::toString, File::new);

    ValueCodec<Pattern, String> PATTERN = new SimpleValueCodec<>(Pattern.class, String.class,
            Pattern::pattern, Pattern::compile);

    ValueCodec<InetAddress, String> INET_ADDR = new SimpleValueCodec<>(InetAddress.class, String.class,
            InetAddress::getHostAddress, raw -> {
                try { return InetAddress.getByName(raw); }
                catch (UnknownHostException e) { throw new JsonException("invalid InetAddress: " + raw, e); }
            });

    ValueCodec<Date, String> DATE = new SimpleValueCodec<>(Date.class, String.class,
            v -> v.toInstant().toString(), raw -> Date.from(Instant.parse(raw)));

    // ──────────────────────────────────────────────────────────────
    //  Complex codecs → private static methods + method reference
    // ──────────────────────────────────────────────────────────────

    ValueCodec<Calendar, String> CALENDAR = new SimpleValueCodec<>(Calendar.class, String.class,
            ValueCodec::_calendarToRaw, ValueCodec::_calendarFromRaw);

    ValueCodec<Optional<?>, Object> OPTIONAL = new OptionalCodec();

    /** Dedicated codec for Optional — null maps to Optional.empty(), not null. */
    @SuppressWarnings("unchecked")
    final class OptionalCodec implements ValueCodec<Optional<?>, Object> {
        @Override
        public Object valueToRaw(Optional<?> value) {
            if (value == null || !value.isPresent()) return null;
            return value.get();
        }
        @Override
        public Optional<?> rawToValue(Object raw) {
            return raw == null ? Optional.empty() : Optional.of(raw);
        }
        @Override public Class<Optional<?>> valueClass() { return (Class) Optional.class; }
        @Override public Class<Object> rawClass() { return Object.class; }
    }

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
