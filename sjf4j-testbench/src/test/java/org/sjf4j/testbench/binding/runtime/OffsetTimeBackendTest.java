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
import java.time.OffsetTime;

import static org.junit.jupiter.api.Assertions.*;

class OffsetTimeBackendTest {

    public static class Times {
        public OffsetTime iso;

        @NodeProperty(codecPattern = "HH.mm.ss XXX")
        public OffsetTime custom;
    }

    private static Binder<?, ?>[] allBackends() {
        return new Binder<?, ?>[]{
                new Jackson2Binder(), new Jackson3Binder(), new SimpleJsonBinder(),
                new GsonBinder(), new JsonpBinder(), new SnakeBinder(),
                new Fastjson2Binder()
        };
    }

    @Test
    void isoAndNullAcrossAllBackends() {
        for (Binder<?, ?> binder : allBackends()) {
            for (String input : new String[]{
                    "00:00Z", "12:34Z", "12:34+05:30", "12:34:56-07:00",
                    "12:34:56.1+05:30", "12:34:56.123+05:30",
                    "12:34:56.123456+05:30",
                    "12:34:56.123456789-03:30"}) {
                assertEquals(OffsetTime.parse(input),
                        binder.readNode("\"" + input + "\"", OffsetTime.class),
                        binder.getClass().getSimpleName() + ": " + input);
            }
            assertNull(binder.readNode("null", OffsetTime.class), binder.getClass().getSimpleName());
        }
    }

    @Test
    void patternAndObjectFieldsAcrossBackends() {
        String input = "{\"iso\":\"12:34:56.123456789+05:30\","
                + "\"custom\":\"12.34.56 +05:30\"}";
        for (Binder<?, ?> binder : allBackends()) {
            Times decoded = (Times) binder.readNode(input, Times.class);
            assertEquals(OffsetTime.parse("12:34:56.123456789+05:30"),
                    decoded.iso, binder.getClass().getSimpleName());
            assertEquals(OffsetTime.parse("12:34:56+05:30"),
                    decoded.custom, binder.getClass().getSimpleName());
            Times nulls = (Times) binder.readNode("{\"iso\":null,\"custom\":null}", Times.class);
            assertNull(nulls.iso, binder.getClass().getSimpleName());
            assertNull(nulls.custom, binder.getClass().getSimpleName());
        }
    }

    @Test
    void simpleEscapesAndBufferSplit() {
        SimpleJsonBinder binder = new SimpleJsonBinder();
        assertEquals(OffsetTime.parse("12:34:56Z"),
                binder.readNode("\"12:34:56\\u005a\"", OffsetTime.class));
        String boundary = " ".repeat(8189) + "\"12:34:56.123456789+05:30\"";
        assertEquals(OffsetTime.parse("12:34:56.123456789+05:30"),
                binder.readNode(new StringReader(boundary), OffsetTime.class));
        assertThrows(RuntimeException.class,
                () -> binder.readNode("\"12:34:56\\x5a\"", OffsetTime.class));
    }
}
