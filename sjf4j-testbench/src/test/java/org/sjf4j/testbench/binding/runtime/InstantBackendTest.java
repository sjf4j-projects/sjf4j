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
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class InstantBackendTest {

    public static class Timestamps {
        public Instant iso;

        @NodeProperty(codecName = "epochMillis")
        public Instant millis;
    }

    private static Binder<?, ?>[] backends() {
        return new Binder<?, ?>[]{
                new Jackson2Binder(), new Jackson3Binder(), new SimpleJsonBinder(),
                new GsonBinder(), new JsonpBinder(), new SnakeBinder(), new Fastjson2Binder()
        };
    }

    @Test
    void canonicalIsoAndNullAcrossSevenBackends() {
        for (Binder<?, ?> binder : backends()) {
            for (String value : new String[]{
                    "1970-01-01T00:00:00Z", "2026-10-31T12:34:56Z",
                    "2026-10-31T12:34:56.1Z", "2026-10-31T12:34:56.123Z",
                    "2026-10-31T12:34:56.123456Z",
                    "2026-10-31T12:34:56.123456789Z"}) {
                assertEquals(Instant.parse(value), binder.readNode("\"" + value + "\"", Instant.class),
                        binder.getClass().getSimpleName() + ": " + value);
            }
            assertNull(binder.readNode("null", Instant.class), binder.getClass().getSimpleName());
        }
    }

    @Test
    void epochMillisCodecAndLegacyDateKeepWorking() {
        String json = "{\"iso\":\"2026-10-31T12:34:56Z\",\"millis\":0}";
        for (Binder<?, ?> binder : backends()) {
            Timestamps bean = (Timestamps) binder.readNode(json, Timestamps.class);
            assertEquals(Instant.parse("2026-10-31T12:34:56Z"), bean.iso,
                    binder.getClass().getSimpleName());
            assertEquals(Instant.EPOCH, bean.millis, binder.getClass().getSimpleName());
            assertEquals(Date.from(Instant.parse("2026-10-31T12:34:56Z")),
                    binder.readNode("\"2026-10-31T12:34:56Z\"", Date.class),
                    binder.getClass().getSimpleName());
        }
    }

    @Test
    void epochMillisRoundTripsAcrossAllBackends() {
        for (Binder<?, ?> binder : backends()) {
            String name = binder.getClass().getSimpleName();
            for (long value : new long[]{
                    0L, 1L, -1L, 1_782_908_096_123L, -1_234_567_890_123L,
                    Long.MIN_VALUE, Long.MAX_VALUE
            }) {
                String input = "{\"millis\":" + value + "}";
                Timestamps decoded = (Timestamps) binder.readNode(input, Timestamps.class);
                assertEquals(Instant.ofEpochMilli(value), decoded.millis, name + ": " + value);
                assertEquals(Long.valueOf(value), Long.valueOf(decoded.millis.toEpochMilli()));
                String serialized = binder.writeNodeAsString(decoded);
                assertTrue(serialized.contains("\"millis\":" + value),
                        name + " must preserve numeric epochMillis: " + serialized);
                Timestamps reread = (Timestamps) binder.readNode(serialized, Timestamps.class);
                assertEquals(decoded.millis, reread.millis, name + ": " + value);
            }
            Timestamps nulls = (Timestamps) binder.readNode("{\"millis\":null}", Timestamps.class);
            assertNull(nulls.millis, name);
        }
    }

    @Test
    void epochMillisRawCodecRetainsLongRepresentation() {
        org.sjf4j.value.ValueCodec<Instant, Long> codec =
                org.sjf4j.value.ValueCodec.INSTANT_EPOCH_MILLIS;
        for (long value : new long[]{0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertEquals(Long.valueOf(value), codec.valueToRaw(Instant.ofEpochMilli(value)));
            assertEquals(Instant.ofEpochMilli(value), codec.rawToValue(value));
        }
        assertNull(codec.rawToValue(null));
        assertNull(codec.valueToRaw(null));
    }

    @Test
    void simpleEscapedCharactersAndReaderBoundary() {
        SimpleJsonBinder binder = new SimpleJsonBinder();
        assertEquals(Instant.parse("2026-10-31T12:34:56Z"),
                binder.readNode("\"2026-10-31T12:34:56\\u005a\"", Instant.class));
        String input = " ".repeat(8189) + "\"2026-10-31T12:34:56.123456789Z\"";
        assertEquals(Instant.parse("2026-10-31T12:34:56.123456789Z"),
                binder.readNode(new StringReader(input), Instant.class));
        assertThrows(RuntimeException.class,
                () -> binder.readNode("\"2026-10-31T12:34:56\\x5a\"", Instant.class));
    }
}
