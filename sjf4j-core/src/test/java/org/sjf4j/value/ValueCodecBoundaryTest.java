package org.sjf4j.value;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.BindingException;
import org.sjf4j.value.ValueCodec;

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

class ValueCodecBoundaryTest {

    @Test
    void convertsStringBackedValuesAndHandlesNulls() throws Exception {
        URI uri = URI.create("https://example.com/a?b=1");
        assertStringCodec(ValueCodec.URI_CODEC, uri, uri.toString(), URI.class);

        URL url = new URL("https://example.com/p?q=1");
        assertStringCodec(ValueCodec.URL_CODEC, url, url.toString(), URL.class);
        assertThrows(BindingException.class, () -> ValueCodec.URL_CODEC.rawToValue(":bad-url"));

        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertStringCodec(ValueCodec.UUID_CODEC, uuid, uuid.toString(), UUID.class);

        Locale locale = Locale.forLanguageTag("zh-CN");
        assertStringCodec(ValueCodec.LOCALE, locale, locale.toLanguageTag(), Locale.class);

        Currency currency = Currency.getInstance("USD");
        assertStringCodec(ValueCodec.CURRENCY, currency, currency.getCurrencyCode(), Currency.class);

        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        assertStringCodec(ValueCodec.ZONE_ID, zoneId, zoneId.getId(), ZoneId.class);

        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        assertStringCodec(ValueCodec.INSTANT_STR, instant, instant.toString(), Instant.class);

        LocalDate localDate = LocalDate.parse("2024-01-01");
        assertStringCodec(ValueCodec.LOCAL_DATE, localDate, localDate.toString(), LocalDate.class);

        LocalDateTime localDateTime = LocalDateTime.parse("2024-01-01T10:00:00");
        assertStringCodec(ValueCodec.LOCAL_DATE_TIME, localDateTime, localDateTime.toString(), LocalDateTime.class);

        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-01-01T10:00:00+08:00");
        assertStringCodec(ValueCodec.OFFSET_DATE_TIME, offsetDateTime, offsetDateTime.toString(), OffsetDateTime.class);

        ZonedDateTime zonedDateTime = ZonedDateTime.parse("2024-01-01T10:00:00+08:00[Asia/Shanghai]");
        assertStringCodec(ValueCodec.ZONED_DATE_TIME, zonedDateTime, zonedDateTime.toString(), ZonedDateTime.class);

        Duration duration = Duration.parse("PT10S");
        assertStringCodec(ValueCodec.DURATION, duration, duration.toString(), Duration.class);

        Period period = Period.parse("P1Y2M3D");
        assertStringCodec(ValueCodec.PERIOD, period, period.toString(), Period.class);

        Path path = Paths.get("/tmp/test.txt");
        assertStringCodec(ValueCodec.PATH, path, path.toString(), Path.class);

        File file = new File("/tmp/test.txt");
        assertStringCodec(ValueCodec.FILE, file, file.toString(), File.class);

        Pattern pattern = Pattern.compile("[a-z]+\\d?");
        assertEquals(Pattern.class, ValueCodec.PATTERN.valueClazz());
        assertEquals(String.class, ValueCodec.PATTERN.rawClazz());
        assertNull(ValueCodec.PATTERN.valueToRaw(null));
        assertNull(ValueCodec.PATTERN.rawToValue(null));
        assertEquals(pattern.pattern(), ValueCodec.PATTERN.valueToRaw(pattern));
        assertEquals(pattern.pattern(), ValueCodec.PATTERN.rawToValue(pattern.pattern()).pattern());

        Date date = new Date(1704103200000L);
        assertStringCodec(ValueCodec.DATE, date, date.toInstant().toString(), Date.class);
    }

    @Test
    void convertsNonStringBackedValuesAndRejectsInvalidInput() throws Exception {
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        assertEquals(Long.class, ValueCodec.INSTANT_EPOCH_MILLIS.rawClazz());
        assertEquals(Instant.class, ValueCodec.INSTANT_EPOCH_MILLIS.valueClazz());
        assertNull(ValueCodec.INSTANT_EPOCH_MILLIS.valueToRaw(null));
        assertNull(ValueCodec.INSTANT_EPOCH_MILLIS.rawToValue(null));
        assertEquals(instant.toEpochMilli(), ValueCodec.INSTANT_EPOCH_MILLIS.valueToRaw(instant));
        assertEquals(instant, ValueCodec.INSTANT_EPOCH_MILLIS.rawToValue(instant.toEpochMilli()));

        InetAddress address = InetAddress.getByName("127.0.0.1");
        assertEquals(String.class, ValueCodec.INET_ADDR.rawClazz());
        assertEquals(InetAddress.class, ValueCodec.INET_ADDR.valueClazz());
        assertNull(ValueCodec.INET_ADDR.valueToRaw(null));
        assertNull(ValueCodec.INET_ADDR.rawToValue(null));
        assertEquals(address.getHostAddress(), ValueCodec.INET_ADDR.valueToRaw(address));
        assertEquals(address, ValueCodec.INET_ADDR.rawToValue(address.getHostAddress()));
        assertThrows(BindingException.class, () -> ValueCodec.INET_ADDR.rawToValue("300.300.300.300"));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.setTimeInMillis(1704103200000L);
        assertEquals(String.class, ValueCodec.CALENDAR.rawClazz());
        assertEquals(Calendar.class, ValueCodec.CALENDAR.valueClazz());
        assertNull(ValueCodec.CALENDAR.valueToRaw(null));
        assertNull(ValueCodec.CALENDAR.rawToValue(null));
        String raw = ValueCodec.CALENDAR.valueToRaw(calendar);
        Calendar decoded = ValueCodec.CALENDAR.rawToValue(raw);
        assertEquals(calendar.getTimeInMillis(), decoded.getTimeInMillis());
        assertEquals(calendar.getTimeZone().getID(), decoded.getTimeZone().getID());
    }

    @Test
    void inetAddressCodecAcceptsNumericLiteralsWithoutDnsResolution() throws Exception {
        InetAddress ipv4 = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        assertEquals(ipv4, ValueCodec.INET_ADDR.rawToValue("127.0.0.1"));
        assertEquals(InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 1}),
                ValueCodec.INET_ADDR.rawToValue("::ffff:192.0.2.1"));

        byte[] loopbackBytes = new byte[16];
        loopbackBytes[15] = 1;
        InetAddress ipv6 = InetAddress.getByAddress(loopbackBytes);
        assertEquals(ipv6, ValueCodec.INET_ADDR.rawToValue("::1"));
        assertEquals(ipv6, ValueCodec.INET_ADDR.rawToValue("[::1]"));
        assertEquals(ipv6, ValueCodec.INET_ADDR.rawToValue("0:0:0:0:0:0:0:1"));
        assertEquals("fe80:0:0:0:0:0:0:1%3",
                ValueCodec.INET_ADDR.rawToValue("fe80::1%3").getHostAddress());

        String[] invalid = {
                "", "localhost", "example.com", "127.0.0.1.example.com", "127.1",
                "01.2.3.4", "300.300.300.300", "[127.0.0.1]",
                ":::1", "1::2::3", "1:2:3:4:5:6:7", "foo:bar", "z::1",
                "fe80::1%9999999999999999999", "::ffff:192.168.0.01",
                "fe80::1%", "fe80::1%3%4", "[::1", "::1]",
                "::ffff:192.0.2.1%3", "::ffff:192.0.2.1%nonexistent-interface",
                "[::ffff:192.0.2.1%3]"
        };
        for (String input : invalid) {
            assertThrows(BindingException.class, () -> ValueCodec.INET_ADDR.rawToValue(input), input);
        }

        Sjf4j sjf4j = Sjf4j.builder().build();
        assertEquals(ipv4, sjf4j.fromJson("\"127.0.0.1\"", InetAddress.class));
        assertEquals(ipv6, sjf4j.fromJson("\"::1\"", InetAddress.class));
        assertThrows(BindingException.class,
                () -> sjf4j.fromJson("\"localhost\"", InetAddress.class));
    }

    @Test
    void defaultValueCopyRetainsReference() {
        URI uri = URI.create("https://example.com/default-copy");
        assertSame(uri, ValueCodec.URI_CODEC.valueCopy(uri));
    }

    @Test
    void nullEncoderAndDecoderReturnNull() {
        // Null encoder/decoder should produce null
        assertNull(ValueCodec.URI_CODEC.valueToRaw(null));
        assertNull(ValueCodec.URI_CODEC.rawToValue(null));
    }

    private static <T> void assertStringCodec(ValueCodec<T, String> codec, T value, String raw, Class<T> valueClass) {
        assertEquals(valueClass, codec.valueClazz());
        assertEquals(String.class, codec.rawClazz());
        assertNull(codec.valueToRaw(null));
        assertNull(codec.rawToValue(null));
        assertEquals(raw, codec.valueToRaw(value));
        assertEquals(value, codec.rawToValue(raw));
    }
}
