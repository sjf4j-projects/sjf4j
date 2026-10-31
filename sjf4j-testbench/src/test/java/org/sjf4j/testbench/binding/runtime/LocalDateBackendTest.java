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
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LocalDateBackendTest {

    public static class PatternDates {
        public LocalDate iso;

        @NodeProperty(codecPattern = "dd/MM/yyyy")
        public LocalDate custom;

        // Explicit patterns override explicitly named ISO codecs.
        @NodeProperty(codecName = "iso", codecPattern = "yyyy/MM/dd")
        public LocalDate patternOverridesFormat;
    }

    @Test
    void explicitPatternsBypassIsoFastPathForAllBackends() {
        String json = "{\"iso\":\"2026-10-31\",\"custom\":\"31/10/2026\","
                + "\"patternOverridesFormat\":\"2026/10/31\"}";
        String invalidCustom = "{\"custom\":\"2026-10-31\"}";
        Binder<?, ?>[] backends = {
                new Jackson2Binder(), new Jackson3Binder(), new SimpleJsonBinder(),
                new GsonBinder(), new JsonpBinder(), new SnakeBinder(), new Fastjson2Binder()
        };
        for (Binder<?, ?> backend : backends) {
            String name = backend.getClass().getSimpleName();
            PatternDates dates = (PatternDates) backend.readNode(json, PatternDates.class);
            assertEquals(LocalDate.of(2026, 10, 31), dates.iso, name);
            assertEquals(LocalDate.of(2026, 10, 31), dates.custom, name);
            assertEquals(LocalDate.of(2026, 10, 31), dates.patternOverridesFormat, name);
            assertThrows(RuntimeException.class,
                    () -> backend.readNode(invalidCustom, PatternDates.class),
                    name + ": an explicit custom pattern must not silently accept ISO");
        }
    }


    @Test
    void allBackendsDecodeCanonicalAndExtendedDates() {
        Binder<?, ?>[] backends = {
                new Jackson2Binder(), new Jackson3Binder(), new SimpleJsonBinder(),
                new GsonBinder(), new JsonpBinder(), new SnakeBinder(), new Fastjson2Binder()
        };
        for (Binder<?, ?> backend : backends) {
            for (String date : new String[]{"2026-10-31", "2000-02-29",
                    "0000-01-01", "+10000-01-01", "-0001-12-31"}) {
                assertEquals(LocalDate.parse(date),
                        backend.readNode("\"" + date + "\"", LocalDate.class),
                        backend.getClass().getSimpleName() + ": " + date);
            }
            assertNull(backend.readNode("null", LocalDate.class),
                    backend.getClass().getSimpleName());
        }
    }

    @Test
    void simpleReaderHandlesBufferBoundariesAndEscapes() {
        SimpleJsonBinder binder = new SimpleJsonBinder();
        // The date crosses the 8192-character input buffer boundary.
        String padding = " ".repeat(8189);
        String json = padding + "\"2026-10-31\"";
        assertEquals(LocalDate.of(2026, 10, 31),
                binder.readNode(new StringReader(json), LocalDate.class));
        assertEquals(LocalDate.of(2026, 10, 31),
                binder.readNode("\"2026-10-\\u00331\"", LocalDate.class));
    }
}
