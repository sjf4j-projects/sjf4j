package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueCodecCoverageTest {

    @Test
    void testStringBackedCodecsNullBranchesAndClassMetadata() throws Exception {
        URI uri = URI.create("https://example.com/a?b=1");
        assertStringCodec(new ValueCodec.UriValueCodec(), uri, uri.toString(), URI.class);

        URL url = new URL("https://example.com/p?q=1");
        assertStringCodec(new ValueCodec.UrlValueCodec(), url, url.toString(), URL.class);
        assertThrows(JsonException.class, () -> new ValueCodec.UrlValueCodec().rawToValue(":bad-url"));

        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertStringCodec(new ValueCodec.UuidValueCodec(), uuid, uuid.toString(), UUID.class);

        Locale locale = Locale.forLanguageTag("zh-CN");
        assertStringCodec(new ValueCodec.LocaleValueCodec(), locale, locale.toLanguageTag(), Locale.class);

        Currency currency = Currency.getInstance("USD");
        assertStringCodec(new ValueCodec.CurrencyValueCodec(), currency, currency.getCurrencyCode(), Currency.class);

        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        assertStringCodec(new ValueCodec.ZoneIdValueCodec(), zoneId, zoneId.getId(), ZoneId.class);

        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        assertStringCodec(new ValueCodec.InstantStringValueCodec(), instant, instant.toString(), Instant.class);

        LocalDate localDate = LocalDate.parse("2024-01-01");
        assertStringCodec(new ValueCodec.LocalDateValueCodec(), localDate, localDate.toString(), LocalDate.class);

        LocalDateTime localDateTime = LocalDateTime.parse("2024-01-01T10:00:00");
        assertStringCodec(new ValueCodec.LocalDateTimeValueCodec(), localDateTime, localDateTime.toString(), LocalDateTime.class);

        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-01-01T10:00:00+08:00");
        assertStringCodec(new ValueCodec.OffsetDateTimeValueCodec(), offsetDateTime, offsetDateTime.toString(), OffsetDateTime.class);

        ZonedDateTime zonedDateTime = ZonedDateTime.parse("2024-01-01T10:00:00+08:00[Asia/Shanghai]");
        assertStringCodec(new ValueCodec.ZonedDateTimeValueCodec(), zonedDateTime, zonedDateTime.toString(), ZonedDateTime.class);

        Duration duration = Duration.parse("PT10S");
        assertStringCodec(new ValueCodec.DurationValueCodec(), duration, duration.toString(), Duration.class);

        Period period = Period.parse("P1Y2M3D");
        assertStringCodec(new ValueCodec.PeriodValueCodec(), period, period.toString(), Period.class);

        Path path = Paths.get("/tmp/test.txt");
        assertStringCodec(new ValueCodec.PathValueCodec(), path, path.toString(), Path.class);

        File file = new File("/tmp/test.txt");
        assertStringCodec(new ValueCodec.FileValueCodec(), file, file.toString(), File.class);

        Pattern pattern = Pattern.compile("[a-z]+\\d?");
        ValueCodec.PatternValueCodec patternCodec = new ValueCodec.PatternValueCodec();
        assertEquals(Pattern.class, patternCodec.valueClass());
        assertEquals(String.class, patternCodec.rawClass());
        assertNull(patternCodec.valueToRaw(null));
        assertNull(patternCodec.rawToValue(null));
        assertEquals(pattern.pattern(), patternCodec.valueToRaw(pattern));
        assertEquals(pattern.pattern(), patternCodec.rawToValue(pattern.pattern()).pattern());

        Date date = new Date(1704103200000L);
        assertStringCodec(new ValueCodec.DateValueCodec(), date, date.toInstant().toString(), Date.class);
    }

    @Test
    void testNonStringBackedCodecsAndErrorBranches() throws Exception {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        ValueCodec.InstantEpochMillisValueCodec epochCodec = new ValueCodec.InstantEpochMillisValueCodec();
        assertEquals(Long.class, epochCodec.rawClass());
        assertEquals(Instant.class, epochCodec.valueClass());
        assertNull(epochCodec.valueToRaw(null));
        assertNull(epochCodec.rawToValue(null));
        assertEquals(instant.toEpochMilli(), epochCodec.valueToRaw(instant));
        assertEquals(instant, epochCodec.rawToValue(instant.toEpochMilli()));

        InetAddress address = InetAddress.getByName("127.0.0.1");
        ValueCodec.InetAddressValueCodec inetCodec = new ValueCodec.InetAddressValueCodec();
        assertEquals(String.class, inetCodec.rawClass());
        assertEquals(InetAddress.class, inetCodec.valueClass());
        assertNull(inetCodec.valueToRaw(null));
        assertNull(inetCodec.rawToValue(null));
        assertEquals(address.getHostAddress(), inetCodec.valueToRaw(address));
        assertEquals(address, inetCodec.rawToValue(address.getHostAddress()));
        assertThrows(JsonException.class, () -> inetCodec.rawToValue("300.300.300.300"));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.setTimeInMillis(1704103200000L);
        ValueCodec.CalendarValueCodec calendarCodec = new ValueCodec.CalendarValueCodec();
        assertEquals(String.class, calendarCodec.rawClass());
        assertEquals(Calendar.class, calendarCodec.valueClass());
        assertNull(calendarCodec.valueToRaw(null));
        assertNull(calendarCodec.rawToValue(null));
        String raw = calendarCodec.valueToRaw(calendar);
        Calendar decoded = calendarCodec.rawToValue(raw);
        assertEquals(calendar.getTimeInMillis(), decoded.getTimeInMillis());
        assertEquals(calendar.getTimeZone().getID(), decoded.getTimeZone().getID());
    }

    @Test
    void testDefaultValueCopyReturnsSameReference() {
        ValueCodec.UriValueCodec codec = new ValueCodec.UriValueCodec();
        URI uri = URI.create("https://example.com/default-copy");
        assertSame(uri, codec.valueCopy(uri));
    }

    private static <T> void assertStringCodec(ValueCodec<T, String> codec, T value, String raw, Class<T> valueClass) {
        assertEquals(valueClass, codec.valueClass());
        assertEquals(String.class, codec.rawClass());
        assertNull(codec.valueToRaw(null));
        assertNull(codec.rawToValue(null));
        assertEquals(raw, codec.valueToRaw(value));
        assertEquals(value, codec.rawToValue(raw));
    }
}
