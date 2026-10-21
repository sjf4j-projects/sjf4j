package org.sjf4j.backend.fastjson2.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.value.ValueInfo;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class StreamingExtendedTemporalCodecTest {
    private static final Fastjson2Binder BINDER = new Fastjson2Binder();

    public static class TemporalPojo {
        public Instant instant;
        public OffsetDateTime offsetDateTime;
        public ZonedDateTime zonedDateTime;
        public OffsetTime offsetTime;
        public Date date;
        public Calendar calendar;

        @NodeProperty(codecPattern = "yyyy/MM/dd HH:mm:ssXXX")
        public OffsetDateTime patternedOffset;

        @NodeProperty(codecPattern = "HH:mm:ssXXX")
        public OffsetTime patternedOffsetTime;
    }

    private static final String JSON =
            "{\"instant\":\"2026-10-21T10:30:01.123Z\","
            + "\"offsetDateTime\":\"2026-10-21T12:30:01.123456789+02:00\","
            + "\"zonedDateTime\":\"2026-10-21T12:30:01.123+02:00[Europe/Paris]\","
            + "\"offsetTime\":\"12:30:01.123456789+02:00\","
            + "\"date\":\"2026-10-21T10:30:01.123Z\","
            + "\"calendar\":\"2026-10-21T12:30:01.123+02:00[Europe/Paris]\","
            + "\"patternedOffset\":\"2026/10/21 12:30:01+02:00\","
            + "\"patternedOffsetTime\":\"12:30:01+02:00\"}";

    @Test
    void selectedIsoCodecsUseStreamReadWithoutChangingWriteFormats() {
        for (Class<?> type : new Class<?>[]{
                Instant.class, OffsetDateTime.class, ZonedDateTime.class,
                OffsetTime.class, Date.class, Calendar.class}) {
            ValueInfo info = TypeRegistry.requireRegisteredValueInfo(type, "iso");
            assertEquals("iso", info.valueFormat, type.getName());
            assertSame(info, TypeRegistry.requireRegisteredValueInfo(type, null));
            assertNotNull(info.streamReader, type.getName());
            assertNull(info.streamWriter, type.getName());
        }
        ValueInfo customized = TypeRegistry.requireRegisteredValueInfo(
                OffsetDateTime.class, null, "yyyy/MM/dd HH:mm:ssXXX");
        assertNull(customized.streamReader);
        assertNull(customized.streamWriter);
        ValueInfo customizedTime = TypeRegistry.requireRegisteredValueInfo(
                OffsetTime.class, null, "HH:mm:ssXXX");
        assertNull(customizedTime.streamReader);
        assertNull(customizedTime.streamWriter);

        assertEquals("epochMillis",
                TypeRegistry.requireRegisteredValueInfo(Instant.class, "epochMillis").valueFormat);
    }

    @Test
    void rootIsoRoundTripsAndNulls() {
        Instant instant = Instant.parse("2026-10-21T10:30:01.123Z");
        OffsetDateTime offset = OffsetDateTime.parse("2026-10-21T12:30:01.123456789+02:00");
        ZonedDateTime zoned = ZonedDateTime.parse("2026-10-21T12:30:01.123+02:00[Europe/Paris]");
        OffsetTime time = OffsetTime.parse("12:30:01.123456789+02:00");
        Date date = Date.from(instant);

        assertEquals(instant, BINDER.readNode("\"" + instant + "\"", Instant.class));
        assertEquals(offset, BINDER.readNode("\"" + offset + "\"", OffsetDateTime.class));
        assertEquals(zoned, BINDER.readNode("\"" + zoned + "\"", ZonedDateTime.class));
        assertEquals(time, BINDER.readNode("\"" + time + "\"", OffsetTime.class));
        assertEquals(date, BINDER.readNode("\"" + instant + "\"", Date.class));
        assertEquals("\"" + instant + "\"", BINDER.writeNodeAsString(instant));
        assertEquals("\"" + offset + "\"", BINDER.writeNodeAsString(offset));
        assertEquals("\"" + zoned + "\"", BINDER.writeNodeAsString(zoned));
        assertEquals("\"" + time + "\"", BINDER.writeNodeAsString(time));
        assertEquals("\"" + instant + "\"", BINDER.writeNodeAsString(date));
        for (Class<?> type : new Class<?>[]{
                Instant.class, OffsetDateTime.class, ZonedDateTime.class,
                OffsetTime.class, Date.class, Calendar.class}) {
            assertNull(BINDER.readNode("null", type), type.getName());
        }
    }

    @Test
    void pojoIsoRoundTripWorksForStringAndUtf8() {
        for (Object parsed : new Object[]{
                BINDER.readNode(JSON, TemporalPojo.class),
                BINDER.readNode(JSON.getBytes(StandardCharsets.UTF_8), TemporalPojo.class)}) {
            TemporalPojo pojo = (TemporalPojo) parsed;
            assertEquals(Instant.parse("2026-10-21T10:30:01.123Z"), pojo.instant);
            assertEquals(OffsetDateTime.parse("2026-10-21T12:30:01.123456789+02:00"), pojo.offsetDateTime);
            assertEquals(ZonedDateTime.parse("2026-10-21T12:30:01.123+02:00[Europe/Paris]"), pojo.zonedDateTime);
            assertEquals(OffsetTime.parse("12:30:01.123456789+02:00"), pojo.offsetTime);
            assertEquals(Date.from(pojo.instant), pojo.date);
            assertEquals("Europe/Paris", pojo.calendar.getTimeZone().getID());
            assertEquals(pojo.instant.toEpochMilli(), pojo.calendar.getTimeInMillis());
            assertEquals(OffsetDateTime.parse("2026-10-21T12:30:01+02:00"), pojo.patternedOffset);
            assertEquals(OffsetTime.parse("12:30:01+02:00"), pojo.patternedOffsetTime);
            assertEquals(JSON, BINDER.writeNodeAsString(pojo));
            assertEquals(JSON, new String(BINDER.writeNodeAsBytes(pojo), StandardCharsets.UTF_8));
        }
    }
}
