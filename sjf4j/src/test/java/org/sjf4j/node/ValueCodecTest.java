package org.sjf4j.node;

import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ValueCodecTest {

    @Test
    public void testJdkValueCodecsPart1() throws Exception {
        NodeRegistry.ValueCodecInfo uriCodec = NodeRegistry.getValueCodecInfo(URI.class);
        NodeRegistry.ValueCodecInfo urlCodec = NodeRegistry.getValueCodecInfo(URL.class);
        NodeRegistry.ValueCodecInfo uuidCodec = NodeRegistry.getValueCodecInfo(UUID.class);

        assertNotNull(uriCodec);
        assertNotNull(urlCodec);
        assertNotNull(uuidCodec);

        URI uri = URI.create("https://example.com/a?b=1");
        String uriRaw = (String) uriCodec.encode(uri);
        assertEquals(uri.toString(), uriRaw);
        assertEquals(uri, uriCodec.decode(uriRaw));

        URL url = new URL("https://example.com/p?q=1");
        String urlRaw = (String) urlCodec.encode(url);
        assertEquals(url.toString(), urlRaw);
        assertEquals(url, urlCodec.decode(urlRaw));

        UUID uuid = UUID.randomUUID();
        String uuidRaw = (String) uuidCodec.encode(uuid);
        assertEquals(uuid.toString(), uuidRaw);
        assertEquals(uuid, uuidCodec.decode(uuidRaw));
    }

    @Test
    public void testJdkValueCodecsPart2() {
        NodeRegistry.ValueCodecInfo localeCodec = NodeRegistry.getValueCodecInfo(Locale.class);
        NodeRegistry.ValueCodecInfo currencyCodec = NodeRegistry.getValueCodecInfo(Currency.class);
        NodeRegistry.ValueCodecInfo zoneIdCodec = NodeRegistry.getValueCodecInfo(ZoneId.class);

        assertNotNull(localeCodec);
        assertNotNull(currencyCodec);
        assertNotNull(zoneIdCodec);

        Locale locale = Locale.forLanguageTag("zh-CN");
        String localeRaw = (String) localeCodec.encode(locale);
        assertEquals(locale.toLanguageTag(), localeRaw);
        assertEquals(locale, localeCodec.decode(localeRaw));

        Currency currency = Currency.getInstance("USD");
        String currencyRaw = (String) currencyCodec.encode(currency);
        assertEquals(currency.getCurrencyCode(), currencyRaw);
        assertEquals(currency, currencyCodec.decode(currencyRaw));

        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        String zoneIdRaw = (String) zoneIdCodec.encode(zoneId);
        assertEquals(zoneId.getId(), zoneIdRaw);
        assertEquals(zoneId, zoneIdCodec.decode(zoneIdRaw));
    }

    @Test
    public void testJdkValueCodecsTime() {
        NodeRegistry.ValueCodecInfo instantCodec = NodeRegistry.getValueCodecInfo(Instant.class);
        NodeRegistry.ValueCodecInfo localDateCodec = NodeRegistry.getValueCodecInfo(LocalDate.class);
        NodeRegistry.ValueCodecInfo localDateTimeCodec = NodeRegistry.getValueCodecInfo(LocalDateTime.class);
        NodeRegistry.ValueCodecInfo offsetDateTimeCodec = NodeRegistry.getValueCodecInfo(OffsetDateTime.class);
        NodeRegistry.ValueCodecInfo zonedDateTimeCodec = NodeRegistry.getValueCodecInfo(ZonedDateTime.class);
        NodeRegistry.ValueCodecInfo durationCodec = NodeRegistry.getValueCodecInfo(Duration.class);
        NodeRegistry.ValueCodecInfo periodCodec = NodeRegistry.getValueCodecInfo(Period.class);

        assertNotNull(instantCodec);
        assertNotNull(localDateCodec);
        assertNotNull(localDateTimeCodec);
        assertNotNull(offsetDateTimeCodec);
        assertNotNull(zonedDateTimeCodec);
        assertNotNull(durationCodec);
        assertNotNull(periodCodec);

        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        Object instantRaw = instantCodec.encode(instant);
        assertEquals(instant, instantCodec.decode(instantRaw));

        LocalDate localDate = LocalDate.parse("2024-01-01");
        String localDateRaw = (String) localDateCodec.encode(localDate);
        assertEquals(localDate.toString(), localDateRaw);
        assertEquals(localDate, localDateCodec.decode(localDateRaw));

        LocalDateTime localDateTime = LocalDateTime.parse("2024-01-01T10:00:00");
        String localDateTimeRaw = (String) localDateTimeCodec.encode(localDateTime);
        assertEquals(localDateTime.toString(), localDateTimeRaw);
        assertEquals(localDateTime, localDateTimeCodec.decode(localDateTimeRaw));

        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-01-01T10:00:00+08:00");
        String offsetDateTimeRaw = (String) offsetDateTimeCodec.encode(offsetDateTime);
        assertEquals(offsetDateTime.toString(), offsetDateTimeRaw);
        assertEquals(offsetDateTime, offsetDateTimeCodec.decode(offsetDateTimeRaw));

        ZonedDateTime zonedDateTime = ZonedDateTime.parse("2024-01-01T10:00:00+08:00[Asia/Shanghai]");
        String zonedDateTimeRaw = (String) zonedDateTimeCodec.encode(zonedDateTime);
        assertEquals(zonedDateTime.toString(), zonedDateTimeRaw);
        assertEquals(zonedDateTime, zonedDateTimeCodec.decode(zonedDateTimeRaw));

        Duration duration = Duration.parse("PT10S");
        String durationRaw = (String) durationCodec.encode(duration);
        assertEquals(duration.toString(), durationRaw);
        assertEquals(duration, durationCodec.decode(durationRaw));

        Period period = Period.parse("P1Y2M3D");
        String periodRaw = (String) periodCodec.encode(period);
        assertEquals(period.toString(), periodRaw);
        assertEquals(period, periodCodec.decode(periodRaw));
    }

    @Test
    public void testJdkValueCodecsPathPatternInet() throws Exception {
        NodeRegistry.ValueCodecInfo pathCodec = NodeRegistry.getValueCodecInfo(Path.class);
        NodeRegistry.ValueCodecInfo fileCodec = NodeRegistry.getValueCodecInfo(File.class);
        NodeRegistry.ValueCodecInfo patternCodec = NodeRegistry.getValueCodecInfo(Pattern.class);
        NodeRegistry.ValueCodecInfo inetAddressCodec = NodeRegistry.getValueCodecInfo(InetAddress.class);

        assertNotNull(pathCodec);
        assertNotNull(fileCodec);
        assertNotNull(patternCodec);
        assertNotNull(inetAddressCodec);

        Path path = Paths.get("/tmp/test.txt");
        String pathRaw = (String) pathCodec.encode(path);
        assertEquals(path.toString(), pathRaw);
        assertEquals(path, pathCodec.decode(pathRaw));

        File file = new File("/tmp/test.txt");
        String fileRaw = (String) fileCodec.encode(file);
        assertEquals(file.toString(), fileRaw);
        assertEquals(file, fileCodec.decode(fileRaw));

        Pattern pattern = Pattern.compile("[a-z]+\\d?");
        String patternRaw = (String) patternCodec.encode(pattern);
        assertEquals(pattern.pattern(), patternRaw);
        assertEquals(pattern.pattern(), ((Pattern) patternCodec.decode(patternRaw)).pattern());

        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        String inetRaw = (String) inetAddressCodec.encode(inetAddress);
        assertEquals(inetAddress.getHostAddress(), inetRaw);
        assertEquals(inetAddress, inetAddressCodec.decode(inetRaw));
    }

    @Test
    public void testJdkValueCodecsDateCalendar() {
        NodeRegistry.ValueCodecInfo dateCodec = NodeRegistry.getValueCodecInfo(Date.class);
        NodeRegistry.ValueCodecInfo calendarCodec = NodeRegistry.getValueCodecInfo(Calendar.class);

        assertNotNull(dateCodec);
        assertNotNull(calendarCodec);

        Date date = new Date(1704103200000L);
        String dateRaw = (String) dateCodec.encode(date);
        assertEquals(date.toInstant().toString(), dateRaw);
        assertEquals(date, dateCodec.decode(dateRaw));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.setTimeInMillis(1704103200000L);
        String calendarRaw = (String) calendarCodec.encode(calendar);
        Calendar decoded = (Calendar) calendarCodec.decode(calendarRaw);
        assertEquals(calendar.getTimeInMillis(), decoded.getTimeInMillis());
        assertEquals(calendar.getTimeZone().getID(), decoded.getTimeZone().getID());
    }

}
