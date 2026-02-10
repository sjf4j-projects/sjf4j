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

public interface ValueCodec<N, R> {

    R encode(N value);

    N decode(R raw);

    Class<N> getValueClass();

    Class<R> getRawClass();

    default N copy(N value) {return value;}


    /// Build-in Values

    // URI
    final class UriValueCodec implements ValueCodec<URI, String> {

        @Override
        public String encode(URI value) {
            return value == null ? null : value.toString();
        }

        @Override
        public URI decode(String raw) {
            return raw == null ? null : URI.create(raw);
        }

        @Override
        public Class<URI> getValueClass() {
            return URI.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // URL
    final class UrlValueCodec implements ValueCodec<URL, String> {

        @Override
        public String encode(URL value) {
            return value == null ? null : value.toString();
        }

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

        @Override
        public Class<URL> getValueClass() {
            return URL.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // UUID
    final class UuidValueCodec implements ValueCodec<UUID, String> {

        @Override
        public String encode(UUID value) {
            return value == null ? null : value.toString();
        }

        @Override
        public UUID decode(String raw) {
            return raw == null ? null : UUID.fromString(raw);
        }

        @Override
        public Class<UUID> getValueClass() {
            return UUID.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Locale
    final class LocaleValueCodec implements ValueCodec<Locale, String> {

        @Override
        public String encode(Locale value) {
            return value == null ? null : value.toLanguageTag();
        }

        @Override
        public Locale decode(String raw) {
            return raw == null ? null : Locale.forLanguageTag(raw);
        }

        @Override
        public Class<Locale> getValueClass() {
            return Locale.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Currency
    final class CurrencyValueCodec implements ValueCodec<Currency, String> {

        @Override
        public String encode(Currency value) {
            return value == null ? null : value.getCurrencyCode();
        }

        @Override
        public Currency decode(String raw) {
            return raw == null ? null : Currency.getInstance(raw);
        }

        @Override
        public Class<Currency> getValueClass() {
            return Currency.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // ZoneId
    final class ZoneIdValueCodec implements ValueCodec<ZoneId, String> {

        @Override
        public String encode(ZoneId value) {
            return value == null ? null : value.getId();
        }

        @Override
        public ZoneId decode(String raw) {
            return raw == null ? null : ZoneId.of(raw);
        }

        @Override
        public Class<ZoneId> getValueClass() {
            return ZoneId.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Instant (String)
    final class InstantStringValueCodec implements ValueCodec<Instant, String> {

        @Override
        public String encode(Instant value) {
            return value == null ? null : value.toString();
        }

        @Override
        public Instant decode(String raw) {
            return raw == null ? null : Instant.parse(raw);
        }

        @Override
        public Class<Instant> getValueClass() {
            return Instant.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Instant (long, EpochMillis)
    final class InstantEpochMillisValueCodec implements ValueCodec<Instant, Long> {

        @Override
        public Long encode(Instant value) {
            return value == null ? null : value.toEpochMilli();
        }

        @Override
        public Instant decode(Long raw) {
            return raw == null ? null : Instant.ofEpochMilli(raw);
        }

        @Override
        public Class<Instant> getValueClass() {
            return Instant.class;
        }

        @Override
        public Class<Long> getRawClass() {
            return Long.class;
        }
    }

    // LocalDate
    final class LocalDateValueCodec implements ValueCodec<LocalDate, String> {

        @Override
        public String encode(LocalDate value) {
            return value == null ? null : value.toString();
        }

        @Override
        public LocalDate decode(String raw) {
            return raw == null ? null : LocalDate.parse(raw);
        }

        @Override
        public Class<LocalDate> getValueClass() {
            return LocalDate.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // LocalDateTime
    final class LocalDateTimeValueCodec implements ValueCodec<LocalDateTime, String> {

        @Override
        public String encode(LocalDateTime value) {
            return value == null ? null : value.toString();
        }

        @Override
        public LocalDateTime decode(String raw) {
            return raw == null ? null : LocalDateTime.parse(raw);
        }

        @Override
        public Class<LocalDateTime> getValueClass() {
            return LocalDateTime.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // OffsetDateTime
    final class OffsetDateTimeValueCodec implements ValueCodec<OffsetDateTime, String> {

        @Override
        public String encode(OffsetDateTime value) {
            return value == null ? null : value.toString();
        }

        @Override
        public OffsetDateTime decode(String raw) {
            return raw == null ? null : OffsetDateTime.parse(raw);
        }

        @Override
        public Class<OffsetDateTime> getValueClass() {
            return OffsetDateTime.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // ZonedDateTime
    final class ZonedDateTimeValueCodec implements ValueCodec<ZonedDateTime, String> {

        @Override
        public String encode(ZonedDateTime value) {
            return value == null ? null : value.toString();
        }

        @Override
        public ZonedDateTime decode(String raw) {
            return raw == null ? null : ZonedDateTime.parse(raw);
        }

        @Override
        public Class<ZonedDateTime> getValueClass() {
            return ZonedDateTime.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Duration
    final class DurationValueCodec implements ValueCodec<Duration, String> {

        @Override
        public String encode(Duration value) {
            return value == null ? null : value.toString();
        }

        @Override
        public Duration decode(String raw) {
            return raw == null ? null : Duration.parse(raw);
        }

        @Override
        public Class<Duration> getValueClass() {
            return Duration.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Period
    final class PeriodValueCodec implements ValueCodec<Period, String> {

        @Override
        public String encode(Period value) {
            return value == null ? null : value.toString();
        }

        @Override
        public Period decode(String raw) {
            return raw == null ? null : Period.parse(raw);
        }

        @Override
        public Class<Period> getValueClass() {
            return Period.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Path
    final class PathValueCodec implements ValueCodec<Path, String> {

        @Override
        public String encode(Path value) {
            return value == null ? null : value.toString();
        }

        @Override
        public Path decode(String raw) {
            return raw == null ? null : Paths.get(raw);
        }

        @Override
        public Class<Path> getValueClass() {
            return Path.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // File
    final class FileValueCodec implements ValueCodec<File, String> {

        @Override
        public String encode(File value) {
            return value == null ? null : value.toString();
        }

        @Override
        public File decode(String raw) {
            return raw == null ? null : new File(raw);
        }

        @Override
        public Class<File> getValueClass() {
            return File.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Pattern
    final class PatternValueCodec implements ValueCodec<Pattern, String> {

        @Override
        public String encode(Pattern value) {
            return value == null ? null : value.pattern();
        }

        @Override
        public Pattern decode(String raw) {
            return raw == null ? null : Pattern.compile(raw);
        }

        @Override
        public Class<Pattern> getValueClass() {
            return Pattern.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // InetAddress
    final class InetAddressValueCodec implements ValueCodec<InetAddress, String> {

        @Override
        public String encode(InetAddress value) {
            return value == null ? null : value.getHostAddress();
        }

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

        @Override
        public Class<InetAddress> getValueClass() {
            return InetAddress.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Date
    final class DateValueCodec implements ValueCodec<Date, String> {

        @Override
        public String encode(Date value) {
            return value == null ? null : value.toInstant().toString();
        }

        @Override
        public Date decode(String raw) {
            return raw == null ? null : Date.from(Instant.parse(raw));
        }

        @Override
        public Class<Date> getValueClass() {
            return Date.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Calendar
    final class CalendarValueCodec implements ValueCodec<Calendar, String> {

        @Override
        public String encode(Calendar value) {
            if (value == null) {
                return null;
            }
            ZoneId zoneId = value.getTimeZone().toZoneId();
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(value.toInstant(), zoneId);
            return zonedDateTime.toString();
        }

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

        @Override
        public Class<Calendar> getValueClass() {
            return Calendar.class;
        }

        @Override
        public Class<String> getRawClass() {
            return String.class;
        }
    }

    // Set
}
