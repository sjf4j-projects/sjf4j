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
import java.time.LocalDate;
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
import java.util.regex.Pattern;

/**
 * Codec contract for mapping domain value types to raw node values.
 * <p>
 * Raw values should be JSON-friendly types supported by SJF4J node model
 * (typically String/Number/Boolean/Map/List/Object). Implementations should be
 * deterministic and reversible for stable serialization/deserialization.
 */
public interface ValueCodec<V, R> {

    /**
     * Encodes a domain value to raw node value.
     */
    R valueToRaw(V value);

    /**
     * Decodes a raw node value back to domain value.
     */
    V rawToValue(R raw);

    /**
     * Returns value type handled by this codec.
     */
    Class<V> valueClass();

    /**
     * Returns raw type produced/consumed by this codec.
     */
    Class<R> rawClass();

    /**
     * Returns a copy of value when custom copy semantics are needed.
     * <p>
     * Default implementation returns the input reference unchanged.
     */
    default V valueCopy(V value) {return value;}


    /// Built-in values

    // URI
    final class UriValueCodec implements ValueCodec<URI, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(URI value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public URI rawToValue(String raw) {
            return raw == null ? null : URI.create(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<URI> valueClass() {
            return URI.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // URL
    final class UrlValueCodec implements ValueCodec<URL, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(URL value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public URL rawToValue(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return new URL(raw);
            } catch (MalformedURLException e) {
                throw new JsonException("Invalid URL: " + raw, e);
            }
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<URL> valueClass() {
            return URL.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // UUID
    final class UuidValueCodec implements ValueCodec<UUID, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(UUID value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public UUID rawToValue(String raw) {
            return raw == null ? null : UUID.fromString(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<UUID> valueClass() {
            return UUID.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Locale
    final class LocaleValueCodec implements ValueCodec<Locale, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Locale value) {
            return value == null ? null : value.toLanguageTag();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Locale rawToValue(String raw) {
            return raw == null ? null : Locale.forLanguageTag(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Locale> valueClass() {
            return Locale.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Currency
    final class CurrencyValueCodec implements ValueCodec<Currency, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Currency value) {
            return value == null ? null : value.getCurrencyCode();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Currency rawToValue(String raw) {
            return raw == null ? null : Currency.getInstance(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Currency> valueClass() {
            return Currency.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // ZoneId
    final class ZoneIdValueCodec implements ValueCodec<ZoneId, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(ZoneId value) {
            return value == null ? null : value.getId();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public ZoneId rawToValue(String raw) {
            return raw == null ? null : ZoneId.of(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<ZoneId> valueClass() {
            return ZoneId.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Instant (String)
    final class InstantStringValueCodec implements ValueCodec<Instant, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Instant value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Instant rawToValue(String raw) {
            return raw == null ? null : Instant.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Instant> valueClass() {
            return Instant.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Instant (long, EpochMillis)
    final class InstantEpochMillisValueCodec implements ValueCodec<Instant, Long> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public Long valueToRaw(Instant value) {
            return value == null ? null : value.toEpochMilli();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Instant rawToValue(Long raw) {
            return raw == null ? null : Instant.ofEpochMilli(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Instant> valueClass() {
            return Instant.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<Long> rawClass() {
            return Long.class;
        }
    }

    // LocalDate
    final class LocalDateValueCodec implements ValueCodec<LocalDate, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(LocalDate value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public LocalDate rawToValue(String raw) {
            return raw == null ? null : LocalDate.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<LocalDate> valueClass() {
            return LocalDate.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // LocalDateTime
    final class LocalDateTimeValueCodec implements ValueCodec<LocalDateTime, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(LocalDateTime value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public LocalDateTime rawToValue(String raw) {
            return raw == null ? null : LocalDateTime.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<LocalDateTime> valueClass() {
            return LocalDateTime.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // OffsetDateTime
    final class OffsetDateTimeValueCodec implements ValueCodec<OffsetDateTime, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(OffsetDateTime value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public OffsetDateTime rawToValue(String raw) {
            return raw == null ? null : OffsetDateTime.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<OffsetDateTime> valueClass() {
            return OffsetDateTime.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // ZonedDateTime
    final class ZonedDateTimeValueCodec implements ValueCodec<ZonedDateTime, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(ZonedDateTime value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public ZonedDateTime rawToValue(String raw) {
            return raw == null ? null : ZonedDateTime.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<ZonedDateTime> valueClass() {
            return ZonedDateTime.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Duration
    final class DurationValueCodec implements ValueCodec<Duration, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Duration value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Duration rawToValue(String raw) {
            return raw == null ? null : Duration.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Duration> valueClass() {
            return Duration.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Period
    final class PeriodValueCodec implements ValueCodec<Period, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Period value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Period rawToValue(String raw) {
            return raw == null ? null : Period.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Period> valueClass() {
            return Period.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Path
    final class PathValueCodec implements ValueCodec<Path, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Path value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Path rawToValue(String raw) {
            return raw == null ? null : Paths.get(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Path> valueClass() {
            return Path.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // File
    final class FileValueCodec implements ValueCodec<File, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(File value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public File rawToValue(String raw) {
            return raw == null ? null : new File(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<File> valueClass() {
            return File.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Pattern
    final class PatternValueCodec implements ValueCodec<Pattern, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Pattern value) {
            return value == null ? null : value.pattern();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Pattern rawToValue(String raw) {
            return raw == null ? null : Pattern.compile(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Pattern> valueClass() {
            return Pattern.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // InetAddress
    final class InetAddressValueCodec implements ValueCodec<InetAddress, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(InetAddress value) {
            return value == null ? null : value.getHostAddress();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public InetAddress rawToValue(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return InetAddress.getByName(raw);
            } catch (UnknownHostException e) {
                throw new JsonException("Invalid InetAddress: " + raw, e);
            }
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<InetAddress> valueClass() {
            return InetAddress.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Date
    final class DateValueCodec implements ValueCodec<Date, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Date value) {
            return value == null ? null : value.toInstant().toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Date rawToValue(String raw) {
            return raw == null ? null : Date.from(Instant.parse(raw));
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Date> valueClass() {
            return Date.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }

    // Calendar
    final class CalendarValueCodec implements ValueCodec<Calendar, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String valueToRaw(Calendar value) {
            if (value == null) return null;
            ZoneId zoneId = value.getTimeZone().toZoneId();
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(value.toInstant(), zoneId);
            return zonedDateTime.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Calendar rawToValue(String raw) {
            if (raw == null) return null;
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(raw);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(zonedDateTime.getZone()));
            calendar.setTimeInMillis(zonedDateTime.toInstant().toEpochMilli());
            return calendar;
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Calendar> valueClass() {
            return Calendar.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> rawClass() {
            return String.class;
        }
    }


}
