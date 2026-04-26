package org.sjf4j;

import org.junit.jupiter.api.Test;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.TypeReference;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Currency;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodecTypesTest {

    private static final Sjf4j SIMPLE_RUNTIME = Sjf4j.builder()
            .jsonFacadeProvider(SimpleJsonFacade.provider())
            .build();

    static class BuiltinCodecBean {
        public URI uri;
        public UUID id;
        public Locale locale;
        public Currency currency;
        public LocalDate date;
        public Instant instant;
        public Duration duration;
    }

    static class InstantContainer {
        public List<Instant> events;
        public Map<String, Instant> checkpoints;
    }

    @Test
    void testBuiltInScalarCodecRoundTrips() throws Exception {
        assertRoundTrip(URI.class, URI.create("https://example.com/a?b=1"), "\"https://example.com/a?b=1\"");
        assertRoundTrip(UUID.class, UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "\"123e4567-e89b-12d3-a456-426614174000\"");
        assertRoundTrip(Locale.class, Locale.forLanguageTag("zh-CN"), "\"zh-CN\"");
        assertRoundTrip(Currency.class, Currency.getInstance("USD"), "\"USD\"");
        assertRoundTrip(Instant.class, Instant.parse("2024-01-01T10:00:00Z"), "\"2024-01-01T10:00:00Z\"");
        assertRoundTrip(LocalDate.class, LocalDate.parse("2024-01-01"), "\"2024-01-01\"");
        assertRoundTrip(LocalDateTime.class, LocalDateTime.parse("2024-01-01T10:00:00"),
                "\"2024-01-01T10:00\"");
        assertRoundTrip(OffsetDateTime.class, OffsetDateTime.parse("2024-01-01T10:00:00+08:00"),
                "\"2024-01-01T10:00+08:00\"");
        assertRoundTrip(ZonedDateTime.class, ZonedDateTime.parse("2024-01-01T10:00:00+08:00[Asia/Shanghai]"),
                "\"2024-01-01T10:00+08:00[Asia/Shanghai]\"");
        assertRoundTrip(Duration.class, Duration.parse("PT10S"), "\"PT10S\"");
        assertRoundTrip(Period.class, Period.parse("P1Y2M3D"), "\"P1Y2M3D\"");
        assertRoundTrip(File.class, new File("/tmp/test.txt"), "\"/tmp/test.txt\"");
        assertRoundTrip(Date.class, new Date(1704103200000L), "\"2024-01-01T10:00:00Z\"");
    }

    @Test
    void testBuiltInScalarCodecsWithCustomEquality() throws Exception {
        Pattern pattern = Pattern.compile("[a-z]+\\d?");
        assertEquals("\"[a-z]+\\\\d?\"", SIMPLE_RUNTIME.toJsonString(pattern));
        assertEquals(pattern.pattern(), SIMPLE_RUNTIME.fromJson("\"[a-z]+\\\\d?\"", Pattern.class).pattern());

    }

    @Test
    void testBuiltInCodecTypesInsidePojo() {
        String json = "{\"uri\":\"https://example.com/a\",\"id\":\"123e4567-e89b-12d3-a456-426614174000\"," +
                "\"locale\":\"zh-CN\",\"currency\":\"USD\"," +
                "\"date\":\"2024-01-01\",\"instant\":\"2024-01-01T10:00:00Z\",\"duration\":\"PT10S\"}";

        BuiltinCodecBean bean = SIMPLE_RUNTIME.fromJson(json, BuiltinCodecBean.class);
        assertEquals(URI.create("https://example.com/a"), bean.uri);
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), bean.id);
        assertEquals(Locale.forLanguageTag("zh-CN"), bean.locale);
        assertEquals(Currency.getInstance("USD"), bean.currency);
        assertEquals(LocalDate.parse("2024-01-01"), bean.date);
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), bean.instant);
        assertEquals(Duration.parse("PT10S"), bean.duration);
        assertEquals(json, SIMPLE_RUNTIME.toJsonString(bean));
    }

    @Test
    void testDefaultValueFormatAppliesInsideContainers() {
        Sjf4j epochMillisRuntime = Sjf4j.builder()
                .jsonFacadeProvider(SimpleJsonFacade.provider())
                .defaultValueFormat(Instant.class, "epochMillis")
                .build();

        Instant first = Instant.parse("2024-01-01T10:00:00Z");
        Instant second = Instant.parse("2024-01-02T10:00:00Z");
        long firstMillis = first.toEpochMilli();
        long secondMillis = second.toEpochMilli();

        String listJson = "[" + firstMillis + "," + secondMillis + "]";
        List<Instant> events = epochMillisRuntime.fromJson(listJson, new TypeReference<List<Instant>>() {});
        assertEquals(Arrays.asList(first, second), events);
        assertEquals(listJson, epochMillisRuntime.toJsonString(events));

        String beanJson = "{\"events\":[" + firstMillis + "," + secondMillis + "]," +
                "\"checkpoints\":{\"start\":" + firstMillis + ",\"end\":" + secondMillis + "}}";
        InstantContainer container = epochMillisRuntime.fromJson(beanJson, InstantContainer.class);
        assertEquals(Arrays.asList(first, second), container.events);
        Map<String, Instant> expected = new LinkedHashMap<>();
        expected.put("start", first);
        expected.put("end", second);
        assertEquals(expected, container.checkpoints);
        assertEquals(beanJson, epochMillisRuntime.toJsonString(container));
    }

    private static <T> void assertRoundTrip(Class<T> type, T value, String json) {
        assertEquals(json, SIMPLE_RUNTIME.toJsonString(value));
        assertEquals(value, SIMPLE_RUNTIME.fromJson(json, type));
    }
}
