package org.sjf4j.backend.fastjson2.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.value.ValueInfo;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class StreamingTemporalCodecTest {
    private static final Fastjson2Binder BINDER = new Fastjson2Binder();

    public static class TemporalPojo {
        public LocalDate date;
        public LocalTime time;
        public LocalDateTime dateTime;

        @NodeProperty(codecPattern = "yyyy/MM/dd")
        public LocalDate customDate;

        @NodeProperty(codecPattern = "HH:mm:ss")
        public LocalTime customTime;

        @NodeProperty(codecPattern = "yyyy/MM/dd HH:mm:ss")
        public LocalDateTime customDateTime;
    }

    @Test
    void namedDefaultsHaveStreamingCallbacksButPatternsDoNot() {
        ValueInfo date = TypeRegistry.requireRegisteredValueInfo(LocalDate.class, "iso");
        ValueInfo time = TypeRegistry.requireRegisteredValueInfo(LocalTime.class, "iso");
        ValueInfo dateTime = TypeRegistry.requireRegisteredValueInfo(LocalDateTime.class, "iso");

        assertEquals(date, TypeRegistry.requireRegisteredValueInfo(LocalDate.class, null));
        assertEquals(time, TypeRegistry.requireRegisteredValueInfo(LocalTime.class, null));
        assertEquals(dateTime, TypeRegistry.requireRegisteredValueInfo(LocalDateTime.class, null));
        assertNotNull(date.streamParser);
        assertNotNull(date.streamGenerator);
        assertNotNull(time.streamParser);
        assertNotNull(time.streamGenerator);
        assertNotNull(dateTime.streamParser);
        assertNotNull(dateTime.streamGenerator); // Backend chooses ISO fallback, not the native space format.

        ValueInfo custom = TypeRegistry.requireRegisteredValueInfo(
                LocalDateTime.class, null, "yyyy/MM/dd HH:mm:ss");
        assertNull(custom.streamParser);
        assertNull(custom.streamGenerator);
        assertEquals("2026/10/21 12:30:01",
                custom.valueToRaw(LocalDateTime.of(2026, 10, 21, 12, 30, 1)));
    }

    @Test
    void rootReadWritePreservesIsoAndNull() {
        assertEquals(LocalDate.of(2026, 10, 21),
                BINDER.readNode("\"2026-10-21\"", LocalDate.class));
        assertEquals(LocalTime.of(12, 30),
                BINDER.readNode("\"12:30\"", LocalTime.class));
        assertEquals(LocalDateTime.of(2026, 10, 21, 12, 30, 1, 123_456_789),
                BINDER.readNode("\"2026-10-21T12:30:01.123456789\"", LocalDateTime.class));

        assertEquals("\"2026-10-21\"", BINDER.writeNodeAsString(LocalDate.of(2026, 10, 21)));
        assertEquals("\"12:30\"", BINDER.writeNodeAsString(LocalTime.of(12, 30)));
        assertEquals("\"12:30:01.123456789\"",
                BINDER.writeNodeAsString(LocalTime.of(12, 30, 1, 123_456_789)));
        assertEquals("\"2026-10-21T12:30\"",
                BINDER.writeNodeAsString(LocalDateTime.of(2026, 10, 21, 12, 30)));

        assertNull(BINDER.readNode("null", LocalDateTime.class));
        assertNull(BINDER.readNode("null", LocalDate.class));
        assertNull(BINDER.readNode("null", LocalTime.class));
    }

    @Test
    void pojoWorksWithUtf8AndCustomPatterns() {
        String json = "{\"date\":\"2026-10-21\","
                + "\"time\":\"12:30\","
                + "\"dateTime\":\"2026-10-21T12:30:01.123456789\","
                + "\"customDate\":\"2026/10/21\","
                + "\"customTime\":\"12:30:00\","
                + "\"customDateTime\":\"2026/10/21 12:30:01\"}";
        TemporalPojo fromString = (TemporalPojo) BINDER.readNode(json, TemporalPojo.class);
        TemporalPojo fromBytes = (TemporalPojo) BINDER.readNode(
                json.getBytes(StandardCharsets.UTF_8), TemporalPojo.class);
        for (TemporalPojo value : new TemporalPojo[]{fromString, fromBytes}) {
            assertEquals(LocalDate.of(2026, 10, 21), value.date);
            assertEquals(LocalTime.of(12, 30), value.time);
            assertEquals(LocalDateTime.of(2026, 10, 21, 12, 30, 1, 123_456_789), value.dateTime);
            assertEquals(LocalDate.of(2026, 10, 21), value.customDate);
            assertEquals(LocalTime.of(12, 30), value.customTime);
            assertEquals(LocalDateTime.of(2026, 10, 21, 12, 30, 1), value.customDateTime);
            assertEquals(json, BINDER.writeNodeAsString(value));
            assertEquals(json, new String(BINDER.writeNodeAsBytes(value), StandardCharsets.UTF_8));
        }
    }
}
