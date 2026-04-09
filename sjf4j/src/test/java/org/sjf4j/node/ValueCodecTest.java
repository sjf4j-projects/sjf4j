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
        NodeRegistry.ValueCodecInfo uriCodec = NodeRegistry.registerValueCodecInfo(URI.class);
        NodeRegistry.ValueCodecInfo urlCodec = NodeRegistry.registerValueCodecInfo(URL.class);
        NodeRegistry.ValueCodecInfo uuidCodec = NodeRegistry.registerValueCodecInfo(UUID.class);

        assertNotNull(uriCodec);
        assertNotNull(urlCodec);
        assertNotNull(uuidCodec);

        URI uri = URI.create("https://example.com/a?b=1");
        String uriRaw = (String) uriCodec.valueToRaw(uri);
        assertEquals(uri.toString(), uriRaw);
        assertEquals(uri, uriCodec.rawToValue(uriRaw));

        URL url = new URL("https://example.com/p?q=1");
        String urlRaw = (String) urlCodec.valueToRaw(url);
        assertEquals(url.toString(), urlRaw);
        assertEquals(url, urlCodec.rawToValue(urlRaw));

        UUID uuid = UUID.randomUUID();
        String uuidRaw = (String) uuidCodec.valueToRaw(uuid);
        assertEquals(uuid.toString(), uuidRaw);
        assertEquals(uuid, uuidCodec.rawToValue(uuidRaw));
    }

    @Test
    public void testJdkValueCodecsPart2() {
        NodeRegistry.ValueCodecInfo localeCodec = NodeRegistry.registerValueCodecInfo(Locale.class);
        NodeRegistry.ValueCodecInfo currencyCodec = NodeRegistry.registerValueCodecInfo(Currency.class);
        NodeRegistry.ValueCodecInfo zoneIdCodec = NodeRegistry.registerValueCodecInfo(ZoneId.class);

        assertNotNull(localeCodec);
        assertNotNull(currencyCodec);
        assertNotNull(zoneIdCodec);

        Locale locale = Locale.forLanguageTag("zh-CN");
        String localeRaw = (String) localeCodec.valueToRaw(locale);
        assertEquals(locale.toLanguageTag(), localeRaw);
        assertEquals(locale, localeCodec.rawToValue(localeRaw));

        Currency currency = Currency.getInstance("USD");
        String currencyRaw = (String) currencyCodec.valueToRaw(currency);
        assertEquals(currency.getCurrencyCode(), currencyRaw);
        assertEquals(currency, currencyCodec.rawToValue(currencyRaw));

        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        String zoneIdRaw = (String) zoneIdCodec.valueToRaw(zoneId);
        assertEquals(zoneId.getId(), zoneIdRaw);
        assertEquals(zoneId, zoneIdCodec.rawToValue(zoneIdRaw));
    }

    @Test
    public void testJdkValueCodecsTime() {
        NodeRegistry.ValueCodecInfo instantCodec = NodeRegistry.registerValueCodecInfo(Instant.class);
        NodeRegistry.ValueCodecInfo localDateCodec = NodeRegistry.registerValueCodecInfo(LocalDate.class);
        NodeRegistry.ValueCodecInfo localDateTimeCodec = NodeRegistry.registerValueCodecInfo(LocalDateTime.class);
        NodeRegistry.ValueCodecInfo offsetDateTimeCodec = NodeRegistry.registerValueCodecInfo(OffsetDateTime.class);
        NodeRegistry.ValueCodecInfo zonedDateTimeCodec = NodeRegistry.registerValueCodecInfo(ZonedDateTime.class);
        NodeRegistry.ValueCodecInfo durationCodec = NodeRegistry.registerValueCodecInfo(Duration.class);
        NodeRegistry.ValueCodecInfo periodCodec = NodeRegistry.registerValueCodecInfo(Period.class);

        assertNotNull(instantCodec);
        assertNotNull(localDateCodec);
        assertNotNull(localDateTimeCodec);
        assertNotNull(offsetDateTimeCodec);
        assertNotNull(zonedDateTimeCodec);
        assertNotNull(durationCodec);
        assertNotNull(periodCodec);

        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        Object instantRaw = instantCodec.valueToRaw(instant);
        assertEquals(instant, instantCodec.rawToValue(instantRaw));

        LocalDate localDate = LocalDate.parse("2024-01-01");
        String localDateRaw = (String) localDateCodec.valueToRaw(localDate);
        assertEquals(localDate.toString(), localDateRaw);
        assertEquals(localDate, localDateCodec.rawToValue(localDateRaw));

        LocalDateTime localDateTime = LocalDateTime.parse("2024-01-01T10:00:00");
        String localDateTimeRaw = (String) localDateTimeCodec.valueToRaw(localDateTime);
        assertEquals(localDateTime.toString(), localDateTimeRaw);
        assertEquals(localDateTime, localDateTimeCodec.rawToValue(localDateTimeRaw));

        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-01-01T10:00:00+08:00");
        String offsetDateTimeRaw = (String) offsetDateTimeCodec.valueToRaw(offsetDateTime);
        assertEquals(offsetDateTime.toString(), offsetDateTimeRaw);
        assertEquals(offsetDateTime, offsetDateTimeCodec.rawToValue(offsetDateTimeRaw));

        ZonedDateTime zonedDateTime = ZonedDateTime.parse("2024-01-01T10:00:00+08:00[Asia/Shanghai]");
        String zonedDateTimeRaw = (String) zonedDateTimeCodec.valueToRaw(zonedDateTime);
        assertEquals(zonedDateTime.toString(), zonedDateTimeRaw);
        assertEquals(zonedDateTime, zonedDateTimeCodec.rawToValue(zonedDateTimeRaw));

        Duration duration = Duration.parse("PT10S");
        String durationRaw = (String) durationCodec.valueToRaw(duration);
        assertEquals(duration.toString(), durationRaw);
        assertEquals(duration, durationCodec.rawToValue(durationRaw));

        Period period = Period.parse("P1Y2M3D");
        String periodRaw = (String) periodCodec.valueToRaw(period);
        assertEquals(period.toString(), periodRaw);
        assertEquals(period, periodCodec.rawToValue(periodRaw));
    }

    @Test
    public void testJdkValueCodecsPathPatternInet() throws Exception {
        NodeRegistry.ValueCodecInfo pathCodec = NodeRegistry.registerValueCodecInfo(Path.class);
        NodeRegistry.ValueCodecInfo fileCodec = NodeRegistry.registerValueCodecInfo(File.class);
        NodeRegistry.ValueCodecInfo patternCodec = NodeRegistry.registerValueCodecInfo(Pattern.class);
        NodeRegistry.ValueCodecInfo inetAddressCodec = NodeRegistry.registerValueCodecInfo(InetAddress.class);

        assertNotNull(pathCodec);
        assertNotNull(fileCodec);
        assertNotNull(patternCodec);
        assertNotNull(inetAddressCodec);

        Path path = Paths.get("/tmp/test.txt");
        String pathRaw = (String) pathCodec.valueToRaw(path);
        assertEquals(path.toString(), pathRaw);
        assertEquals(path, pathCodec.rawToValue(pathRaw));

        File file = new File("/tmp/test.txt");
        String fileRaw = (String) fileCodec.valueToRaw(file);
        assertEquals(file.toString(), fileRaw);
        assertEquals(file, fileCodec.rawToValue(fileRaw));

        Pattern pattern = Pattern.compile("[a-z]+\\d?");
        String patternRaw = (String) patternCodec.valueToRaw(pattern);
        assertEquals(pattern.pattern(), patternRaw);
        assertEquals(pattern.pattern(), ((Pattern) patternCodec.rawToValue(patternRaw)).pattern());

        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        String inetRaw = (String) inetAddressCodec.valueToRaw(inetAddress);
        assertEquals(inetAddress.getHostAddress(), inetRaw);
        assertEquals(inetAddress, inetAddressCodec.rawToValue(inetRaw));
    }

    @Test
    public void testJdkValueCodecsDateCalendar() {
        NodeRegistry.ValueCodecInfo dateCodec = NodeRegistry.registerValueCodecInfo(Date.class);
        NodeRegistry.ValueCodecInfo calendarCodec = NodeRegistry.registerValueCodecInfo(Calendar.class);

        assertNotNull(dateCodec);
        assertNotNull(calendarCodec);

        Date date = new Date(1704103200000L);
        String dateRaw = (String) dateCodec.valueToRaw(date);
        assertEquals(date.toInstant().toString(), dateRaw);
        assertEquals(date, dateCodec.rawToValue(dateRaw));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.setTimeInMillis(1704103200000L);
        String calendarRaw = (String) calendarCodec.valueToRaw(calendar);
        Calendar decoded = (Calendar) calendarCodec.rawToValue(calendarRaw);
        assertEquals(calendar.getTimeInMillis(), decoded.getTimeInMillis());
        assertEquals(calendar.getTimeZone().getID(), decoded.getTimeZone().getID());
    }

}
