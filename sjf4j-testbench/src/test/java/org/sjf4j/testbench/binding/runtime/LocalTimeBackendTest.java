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
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class LocalTimeBackendTest {

    public static class Times {
        public LocalTime iso;

        @NodeProperty(codecPattern = "HH.mm.ss")
        public LocalTime custom;

        @NodeProperty(codecName = "iso", codecPattern = "HH-mm-ss")
        public LocalTime patternOverridesFormat;
    }

    private static Binder<?, ?>[] backends() {
        return new Binder<?, ?>[]{
                new Jackson2Binder(), new Jackson3Binder(), new SimpleJsonBinder(),
                new GsonBinder(), new JsonpBinder(), new SnakeBinder(), new Fastjson2Binder()
        };
    }

    @Test
    void allBackendsDecodeIsoTimesAndNulls() {
        for (Binder<?, ?> backend : backends()) {
            for (String time : new String[]{
                    "00:00", "23:59", "12:34:56", "12:34:56.1",
                    "12:34:56.123", "12:34:56.123456",
                    "12:34:56.123456789"}) {
                assertEquals(LocalTime.parse(time),
                        backend.readNode("\"" + time + "\"", LocalTime.class),
                        backend.getClass().getSimpleName() + ": " + time);
            }
            assertNull(backend.readNode("null", LocalTime.class),
                    backend.getClass().getSimpleName());
        }
    }

    @Test
    void allBackendsPreserveExplicitPatterns() {
        String json = "{\"iso\":\"12:34:56\",\"custom\":\"12.34.56\","
                + "\"patternOverridesFormat\":\"12-34-56\"}";
        for (Binder<?, ?> backend : backends()) {
            String name = backend.getClass().getSimpleName();
            Times bean = (Times) backend.readNode(json, Times.class);
            assertEquals(LocalTime.of(12, 34, 56), bean.iso, name);
            assertEquals(LocalTime.of(12, 34, 56), bean.custom, name);
            assertEquals(LocalTime.of(12, 34, 56), bean.patternOverridesFormat, name);
            assertThrows(RuntimeException.class,
                    () -> backend.readNode("{\"custom\":\"12:34:56\"}", Times.class),
                    name + ": explicit custom pattern must reject ISO");
        }
    }

    @Test
    void simpleHandlesEscapesAndBufferBoundaries() {
        SimpleJsonBinder binder = new SimpleJsonBinder();
        String json = " ".repeat(8189) + "\"12:34:56.123\"";
        assertEquals(LocalTime.of(12, 34, 56, 123_000_000),
                binder.readNode(new StringReader(json), LocalTime.class));
        assertEquals(LocalTime.of(12, 34, 56),
                binder.readNode("\"12:34:5\\u0036\"", LocalTime.class));
    }
}
