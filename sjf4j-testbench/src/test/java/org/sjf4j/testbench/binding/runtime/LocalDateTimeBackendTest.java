package org.sjf4j.testbench.binding.runtime;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;

import java.io.StringReader;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LocalDateTimeBackendTest {

    public static class Times {
        public LocalDateTime iso;

        @NodeProperty(codecPattern = "yyyy/MM/dd HH.mm.ss")
        public LocalDateTime custom;

        @NodeProperty(codecName = "iso", codecPattern = "dd-MM-yyyy HH:mm")
        public LocalDateTime patternOverridesFormat;
    }

    private static Binder<?, ?>[] backends() {
        return new Binder<?, ?>[]{
                new Jackson2Binder(), new Jackson3Binder(), new SimpleJsonBinder(),
                new GsonBinder(), new JsonpBinder(), new SnakeBinder(), new Fastjson2Binder()
        };
    }

    @Test
    void eachBackendReadsIsoLocalDateTimeAndNull() {
        for (Binder<?, ?> backend : backends()) {
            for (String value : new String[]{
                    "2026-10-31T12:34", "2026-10-31T12:34:56",
                    "2000-02-29T23:59:59", "0000-01-01T00:00",
                    "2026-10-31T12:34:56.1", "2026-10-31T12:34:56.123",
                    "2026-10-31T12:34:56.123456",
                    "2026-10-31T12:34:56.123456789"}) {
                assertEquals(LocalDateTime.parse(value),
                        backend.readNode("\"" + value + "\"", LocalDateTime.class),
                        backend.getClass().getSimpleName() + ": " + value);
            }
            assertNull(backend.readNode("null", LocalDateTime.class),
                    backend.getClass().getSimpleName());
        }
    }

    @Test
    void explicitPatternsOverrideDefaultIsoDecoder() {
        String json = "{\"iso\":\"2026-10-31T12:34:56\","
                + "\"custom\":\"2026/10/31 12.34.56\","
                + "\"patternOverridesFormat\":\"31-10-2026 12:34\"}";
        for (Binder<?, ?> backend : backends()) {
            String name = backend.getClass().getSimpleName();
            Times bean = (Times) backend.readNode(json, Times.class);
            assertEquals(LocalDateTime.of(2026, 10, 31, 12, 34, 56), bean.iso, name);
            assertEquals(LocalDateTime.of(2026, 10, 31, 12, 34, 56), bean.custom, name);
            assertEquals(LocalDateTime.of(2026, 10, 31, 12, 34), bean.patternOverridesFormat, name);
            assertThrows(RuntimeException.class,
                    () -> backend.readNode("{\"custom\":\"2026-10-31T12:34:56\"}", Times.class),
                    name + ": explicit pattern must reject default ISO text");
        }
    }

    @Test
    void simpleHandlesEscapesAndReaderBufferSplits() {
        SimpleJsonBinder binder = new SimpleJsonBinder();
        assertEquals(LocalDateTime.of(2026, 10, 31, 12, 34, 56),
                binder.readNode("\"2026-10-31T12:34:5\\u0036\"", LocalDateTime.class));
        String json = " ".repeat(8189) + "\"2026-10-31T12:34:56.123456789\"";
        assertEquals(LocalDateTime.of(2026, 10, 31, 12, 34, 56, 123456789),
                binder.readNode(new StringReader(json), LocalDateTime.class));
        assertThrows(RuntimeException.class,
                () -> binder.readNode("\"2026-10-31T12:34:5\\x36\"", LocalDateTime.class));
    }
}
