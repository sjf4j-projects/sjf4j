package org.sjf4j.node;

import org.sjf4j.exception.JsonException;

import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
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
 * Codec for mapping custom value types to JSON-friendly raw types.
 */
public interface ValueCodec<N, R> {

    /**
     * Encodes a value object to raw JSON-friendly form.
     */
    R encode(N value);

    /**
     * Decodes a raw JSON-friendly value to value object.
     */
    N decode(R raw);

    /**
     * Returns value type handled by this codec.
     */
    Class<N> getValueClass();

    /**
     * Returns raw type produced/consumed by this codec.
     */
    Class<R> getRawClass();

    /**
     * Returns a copy of value when codec needs custom copy semantics.
     */
    default N copy(N value) {return value;}


    /// Built-in values

    // URI
    final class UriValueCodec implements ValueCodec<URI, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(URI value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public URI decode(String raw) {
            return raw == null ? null : URI.create(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<URI> getValueClass() {
            return URI.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // URL
    final class UrlValueCodec implements ValueCodec<URL, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(URL value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public URL decode(String raw) {
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
        public Class<URL> getValueClass() {
            return URL.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // UUID
    final class UuidValueCodec implements ValueCodec<UUID, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(UUID value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public UUID decode(String raw) {
            return raw == null ? null : UUID.fromString(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<UUID> getValueClass() {
            return UUID.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Locale
    final class LocaleValueCodec implements ValueCodec<Locale, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Locale value) {
            return value == null ? null : value.toLanguageTag();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Locale decode(String raw) {
            return raw == null ? null : Locale.forLanguageTag(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Locale> getValueClass() {
            return Locale.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Currency
    final class CurrencyValueCodec implements ValueCodec<Currency, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Currency value) {
            return value == null ? null : value.getCurrencyCode();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Currency decode(String raw) {
            return raw == null ? null : Currency.getInstance(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Currency> getValueClass() {
            return Currency.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // ZoneId
    final class ZoneIdValueCodec implements ValueCodec<ZoneId, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(ZoneId value) {
            return value == null ? null : value.getId();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public ZoneId decode(String raw) {
            return raw == null ? null : ZoneId.of(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<ZoneId> getValueClass() {
            return ZoneId.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Instant (String)
    final class InstantStringValueCodec implements ValueCodec<Instant, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Instant value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Instant decode(String raw) {
            return raw == null ? null : Instant.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Instant> getValueClass() {
            return Instant.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Instant (long, EpochMillis)
    final class InstantEpochMillisValueCodec implements ValueCodec<Instant, Long> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public Long encode(Instant value) {
            return value == null ? null : value.toEpochMilli();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Instant decode(Long raw) {
            return raw == null ? null : Instant.ofEpochMilli(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Instant> getValueClass() {
            return Instant.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<Long> getRawClass() {
            return Long.class;
        }
    }

    // LocalDate
    final class LocalDateValueCodec implements ValueCodec<LocalDate, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(LocalDate value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public LocalDate decode(String raw) {
            return raw == null ? null : LocalDate.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<LocalDate> getValueClass() {
            return LocalDate.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // LocalDateTime
    final class LocalDateTimeValueCodec implements ValueCodec<LocalDateTime, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(LocalDateTime value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public LocalDateTime decode(String raw) {
            return raw == null ? null : LocalDateTime.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<LocalDateTime> getValueClass() {
            return LocalDateTime.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // OffsetDateTime
    final class OffsetDateTimeValueCodec implements ValueCodec<OffsetDateTime, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(OffsetDateTime value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public OffsetDateTime decode(String raw) {
            return raw == null ? null : OffsetDateTime.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<OffsetDateTime> getValueClass() {
            return OffsetDateTime.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // ZonedDateTime
    final class ZonedDateTimeValueCodec implements ValueCodec<ZonedDateTime, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(ZonedDateTime value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public ZonedDateTime decode(String raw) {
            return raw == null ? null : ZonedDateTime.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<ZonedDateTime> getValueClass() {
            return ZonedDateTime.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Duration
    final class DurationValueCodec implements ValueCodec<Duration, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Duration value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Duration decode(String raw) {
            return raw == null ? null : Duration.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Duration> getValueClass() {
            return Duration.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Period
    final class PeriodValueCodec implements ValueCodec<Period, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Period value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Period decode(String raw) {
            return raw == null ? null : Period.parse(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Period> getValueClass() {
            return Period.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Path
    final class PathValueCodec implements ValueCodec<Path, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Path value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Path decode(String raw) {
            return raw == null ? null : Paths.get(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Path> getValueClass() {
            return Path.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // File
    final class FileValueCodec implements ValueCodec<File, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(File value) {
            return value == null ? null : value.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public File decode(String raw) {
            return raw == null ? null : new File(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<File> getValueClass() {
            return File.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Pattern
    final class PatternValueCodec implements ValueCodec<Pattern, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Pattern value) {
            return value == null ? null : value.pattern();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Pattern decode(String raw) {
            return raw == null ? null : Pattern.compile(raw);
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Pattern> getValueClass() {
            return Pattern.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // InetAddress
    final class InetAddressValueCodec implements ValueCodec<InetAddress, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(InetAddress value) {
            return value == null ? null : value.getHostAddress();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public InetAddress decode(String raw) {
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
        public Class<InetAddress> getValueClass() {
            return InetAddress.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Date
    final class DateValueCodec implements ValueCodec<Date, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Date value) {
            return value == null ? null : value.toInstant().toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Date decode(String raw) {
            return raw == null ? null : Date.from(Instant.parse(raw));
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Date> getValueClass() {
            return Date.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Calendar
    final class CalendarValueCodec implements ValueCodec<Calendar, String> {

        /**
         * Encodes value into raw representation.
         */
        @Override
        public String encode(Calendar value) {
            if (value == null) {
                return null;
            }
            ZoneId zoneId = value.getTimeZone().toZoneId();
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(value.toInstant(), zoneId);
            return zonedDateTime.toString();
        }

        /**
         * Decodes raw representation into value.
         */
        @Override
        public Calendar decode(String raw) {
            if (raw == null) {
                return null;
            }
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(raw);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(zonedDateTime.getZone()));
            calendar.setTimeInMillis(zonedDateTime.toInstant().toEpochMilli());
            return calendar;
        }

        /**
         * Returns value class handled by this codec.
         */
        @Override
        public Class<Calendar> getValueClass() {
            return Calendar.class;
        }

        /**
         * Returns raw class handled by this codec.
         */
        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Set
}
