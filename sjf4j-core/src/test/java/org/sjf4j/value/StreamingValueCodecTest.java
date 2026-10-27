package org.sjf4j.value;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.meta.TypeRegistry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Streaming codec selection and plain-codec fallback contracts. */
class StreamingValueCodecTest {
    static final class Key {
        final String value;
        Key(String value) { this.value = value; }
    }

    @Test
    void streamingCodecRetainsOrdinaryRawConversionsAndUsesStreamIO() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        ValueCodec<Key, String> codec = new ValueCodec.StreamingValueCodec<>(
                Key.class, String.class, v -> v.value, Key::new,
                reader -> {
                    reads.incrementAndGet();
                    String raw = reader.readString();
                    return raw == null ? null : new Key(raw);
                },
                (writer, value) -> {
                    writes.incrementAndGet();
                    writer.writeString(value.value);
                });
        ValueRegistry.registerByCodec(codec, "stream", true);

        ValueInfo info = TypeRegistry.requireRegisteredValueInfo(Key.class, "stream");
        assertSame(codec, info.codec);
        assertNotNull(info.streamParser);
        assertNotNull(info.streamGenerator);
        assertEquals("alpha", ((Key) info.rawToValue("alpha")).value);
        assertEquals("alpha", info.valueToRaw(new Key("alpha")));

        SimpleJsonBinder binder = new SimpleJsonBinder(RuntimeContext.EMPTY);
        assertEquals("alpha", ((Key) binder.readNode("\"alpha\"", Key.class)).value);
        assertEquals("\"alpha\"", binder.writeNodeAsString(new Key("alpha")));
        assertNull(binder.readNode("null", Key.class));
        assertEquals(2, reads.get());
        assertEquals(1, writes.get());
    }

    static final class Fallback {
        final String value;
        Fallback(String value) { this.value = value; }
    }

    @Test
    void missingStreamingDirectionsUseTheRawValueCodecPath() {
        ValueCodec<Fallback, String> codec = new ValueCodec.StreamingValueCodec<>(
                Fallback.class, String.class, v -> v.value, Fallback::new,
                null, null);
        ValueRegistry.registerByCodec(codec, "fallback", true);
        ValueInfo info = TypeRegistry.requireRegisteredValueInfo(Fallback.class, null);
        assertNull(info.streamParser);
        assertNull(info.streamGenerator);

        SimpleJsonBinder binder = new SimpleJsonBinder(RuntimeContext.EMPTY);
        assertEquals("beta", ((Fallback) binder.readNode("\"beta\"", Fallback.class)).value);
        assertEquals("\"beta\"", binder.writeNodeAsString(new Fallback("beta")));
    }
}
