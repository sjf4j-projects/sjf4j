package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.value.NodeValueCodec;
import org.sjf4j.value.PatternedValueCodec;

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

class NodeValueCodecEdgeCaseTest {

    @Test
    void convertsStringBackedValuesAndHandlesNulls() throws Exception {
        URI uri = URI.create("https://example.com/a?b=1");
        assertStringCodec(NodeValueCodec.URI_CODEC, uri, uri.toString(), URI.class);

        URL url = new URL("https://example.com/p?q=1");
        assertStringCodec(NodeValueCodec.URL_CODEC, url, url.toString(), URL.class);
        assertThrows(JsonException.class, () -> NodeValueCodec.URL_CODEC.rawToValue(":bad-url"));

        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertStringCodec(NodeValueCodec.UUID_CODEC, uuid, uuid.toString(), UUID.class);

        Locale locale = Locale.forLanguageTag("zh-CN");
        assertStringCodec(NodeValueCodec.LOCALE, locale, locale.toLanguageTag(), Locale.class);

        Currency currency = Currency.getInstance("USD");
        assertStringCodec(NodeValueCodec.CURRENCY, currency, currency.getCurrencyCode(), Currency.class);

        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        assertStringCodec(NodeValueCodec.ZONE_ID, zoneId, zoneId.getId(), ZoneId.class);

        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        assertStringCodec(NodeValueCodec.INSTANT_STR, instant, instant.toString(), Instant.class);

        LocalDate localDate = LocalDate.parse("2024-01-01");
        assertStringCodec(PatternedValueCodec.LOCAL_DATE, localDate, localDate.toString(), LocalDate.class);

        LocalDateTime localDateTime = LocalDateTime.parse("2024-01-01T10:00:00");
        assertStringCodec(PatternedValueCodec.LOCAL_DATE_TIME, localDateTime, localDateTime.toString(), LocalDateTime.class);

        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-01-01T10:00:00+08:00");
        assertStringCodec(PatternedValueCodec.OFFSET_DATE_TIME, offsetDateTime, offsetDateTime.toString(), OffsetDateTime.class);

        ZonedDateTime zonedDateTime = ZonedDateTime.parse("2024-01-01T10:00:00+08:00[Asia/Shanghai]");
        assertStringCodec(PatternedValueCodec.ZONED_DATE_TIME, zonedDateTime, zonedDateTime.toString(), ZonedDateTime.class);

        Duration duration = Duration.parse("PT10S");
        assertStringCodec(NodeValueCodec.DURATION, duration, duration.toString(), Duration.class);

        Period period = Period.parse("P1Y2M3D");
        assertStringCodec(NodeValueCodec.PERIOD, period, period.toString(), Period.class);

        Path path = Paths.get("/tmp/test.txt");
        assertStringCodec(NodeValueCodec.PATH, path, path.toString(), Path.class);

        File file = new File("/tmp/test.txt");
        assertStringCodec(NodeValueCodec.FILE, file, file.toString(), File.class);

        Pattern pattern = Pattern.compile("[a-z]+\\d?");
        assertEquals(Pattern.class, NodeValueCodec.PATTERN.valueClazz());
        assertEquals(String.class, NodeValueCodec.PATTERN.rawClazz());
        assertNull(NodeValueCodec.PATTERN.valueToRaw(null));
        assertNull(NodeValueCodec.PATTERN.rawToValue(null));
        assertEquals(pattern.pattern(), NodeValueCodec.PATTERN.valueToRaw(pattern));
        assertEquals(pattern.pattern(), NodeValueCodec.PATTERN.rawToValue(pattern.pattern()).pattern());

        Date date = new Date(1704103200000L);
        assertStringCodec(NodeValueCodec.DATE, date, date.toInstant().toString(), Date.class);
    }

    @Test
    void convertsNonStringBackedValuesAndRejectsInvalidInput() throws Exception {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        assertEquals(Long.class, NodeValueCodec.INSTANT_EPOCH_MILLIS.rawClazz());
        assertEquals(Instant.class, NodeValueCodec.INSTANT_EPOCH_MILLIS.valueClazz());
        assertNull(NodeValueCodec.INSTANT_EPOCH_MILLIS.valueToRaw(null));
        assertNull(NodeValueCodec.INSTANT_EPOCH_MILLIS.rawToValue(null));
        assertEquals(instant.toEpochMilli(), NodeValueCodec.INSTANT_EPOCH_MILLIS.valueToRaw(instant));
        assertEquals(instant, NodeValueCodec.INSTANT_EPOCH_MILLIS.rawToValue(instant.toEpochMilli()));

        InetAddress address = InetAddress.getByName("127.0.0.1");
        assertEquals(String.class, NodeValueCodec.INET_ADDR.rawClazz());
        assertEquals(InetAddress.class, NodeValueCodec.INET_ADDR.valueClazz());
        assertNull(NodeValueCodec.INET_ADDR.valueToRaw(null));
        assertNull(NodeValueCodec.INET_ADDR.rawToValue(null));
        assertEquals(address.getHostAddress(), NodeValueCodec.INET_ADDR.valueToRaw(address));
        assertEquals(address, NodeValueCodec.INET_ADDR.rawToValue(address.getHostAddress()));
        assertThrows(JsonException.class, () -> NodeValueCodec.INET_ADDR.rawToValue("300.300.300.300"));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.setTimeInMillis(1704103200000L);
        assertEquals(String.class, NodeValueCodec.CALENDAR.rawClazz());
        assertEquals(Calendar.class, NodeValueCodec.CALENDAR.valueClazz());
        assertNull(NodeValueCodec.CALENDAR.valueToRaw(null));
        assertNull(NodeValueCodec.CALENDAR.rawToValue(null));
        String raw = NodeValueCodec.CALENDAR.valueToRaw(calendar);
        Calendar decoded = NodeValueCodec.CALENDAR.rawToValue(raw);
        assertEquals(calendar.getTimeInMillis(), decoded.getTimeInMillis());
        assertEquals(calendar.getTimeZone().getID(), decoded.getTimeZone().getID());
    }

    @Test
    void defaultValueCopyRetainsReference() {
        URI uri = URI.create("https://example.com/default-copy");
        assertSame(uri, NodeValueCodec.URI_CODEC.valueCopy(uri));
    }

    @Test
    void nullEncoderAndDecoderReturnNull() {
        // Null encoder/decoder should produce null
        assertNull(NodeValueCodec.URI_CODEC.valueToRaw(null));
        assertNull(NodeValueCodec.URI_CODEC.rawToValue(null));
    }

    private static <T> void assertStringCodec(NodeValueCodec<T, String> codec, T value, String raw, Class<T> valueClass) {
        assertEquals(valueClass, codec.valueClazz());
        assertEquals(String.class, codec.rawClazz());
        assertNull(codec.valueToRaw(null));
        assertNull(codec.rawToValue(null));
        assertEquals(raw, codec.valueToRaw(value));
        assertEquals(value, codec.rawToValue(raw));
    }
}
