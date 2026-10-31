package org.sjf4j.testbench.binding.runtime;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.backend.snake.binding.SnakeBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.StringReader;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OffsetDateTimeBackendTest {

    public static class Values {
        public OffsetDateTime timestamp;

        @NodeProperty(codecPattern = "yyyy/MM/dd HH:mm:ss XXX")
        public OffsetDateTime custom;
    }

    private static Binder<?, ?>[] backends() {
        return new Binder<?, ?>[]{
                new Jackson2Binder(), new Jackson3Binder(), new SimpleJsonBinder(),
                new GsonBinder(), new JsonpBinder(), new SnakeBinder(), new Fastjson2Binder()
        };
    }

    @Test
    void canonicalShapesAndNullAcrossBackends() {
        for (Binder<?, ?> binder : backends()) {
            for (String value : new String[]{
                    "1970-01-01T00:00Z", "2026-10-31T12:34+08:00",
                    "2026-10-31T12:34:56-05:30",
                    "2026-10-31T12:34:56.1Z",
                    "2026-10-31T12:34:56.123+05:30",
                    "2026-10-31T12:34:56.123456-07:00",
                    "2026-10-31T12:34:56.123456789Z"}) {
                assertEquals(OffsetDateTime.parse(value),
                        binder.readNode("\"" + value + "\"", OffsetDateTime.class),
                        binder.getClass().getSimpleName() + ": " + value);
            }
            assertNull(binder.readNode("null", OffsetDateTime.class),
                    binder.getClass().getSimpleName());
        }
    }

    @Test
    void customPatternAndObjectPropertiesAcrossBackends() {
        String input = "{\"timestamp\":\"2026-10-31T12:34:56.123+05:30\","
                + "\"custom\":\"2026/10/31 12:34:56 +05:30\"}";
        for (Binder<?, ?> binder : backends()) {
            Values value = (Values) binder.readNode(input, Values.class);
            assertEquals(OffsetDateTime.parse("2026-10-31T12:34:56.123+05:30"),
                    value.timestamp, binder.getClass().getSimpleName());
            assertEquals(OffsetDateTime.parse("2026-10-31T12:34:56+05:30"),
                    value.custom, binder.getClass().getSimpleName());
            Values nullValue = (Values) binder.readNode("{\"timestamp\":null,\"custom\":null}", Values.class);
            assertNull(nullValue.timestamp);
            assertNull(nullValue.custom);
        }
    }

    @Test
    void simpleParserHandlesEscapesAndBoundarySplit() {
        SimpleJsonBinder binder = new SimpleJsonBinder();
        assertEquals(OffsetDateTime.parse("2026-10-31T12:34:56Z"),
                binder.readNode("\"2026-10-31T12:34:56\\u005a\"", OffsetDateTime.class));
        String split = " ".repeat(8189) + "\"2026-10-31T12:34:56.123456789+05:30\"";
        assertEquals(OffsetDateTime.parse("2026-10-31T12:34:56.123456789+05:30"),
                binder.readNode(new StringReader(split), OffsetDateTime.class));
        assertThrows(RuntimeException.class,
                () -> binder.readNode("\"2026-10-31T12:34:56\\x5a\"", OffsetDateTime.class));
    }
}
