package org.sjf4j.binding.contract;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.value.ValueRegistry;
import org.sjf4j.value.ValueCodec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** User-registered atomic-value bindings via SJF4J's ValueCodec extension API. */
public abstract class JDKAtomicTypesDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    private static final class AtomicCodecs {
        static {
            ValueRegistry.registerByCodec(new ValueCodec.SimpleValueCodec<>(
                    AtomicBoolean.class, Boolean.class, AtomicBoolean::get, AtomicBoolean::new), null, false);
            ValueRegistry.registerByCodec(new ValueCodec.SimpleValueCodec<>(
                    AtomicInteger.class, Integer.class, AtomicInteger::get, AtomicInteger::new), null, false);
            ValueRegistry.registerByCodec(new ValueCodec.SimpleValueCodec<>(
                    AtomicLong.class, Long.class, AtomicLong::get, AtomicLong::new), null, false);
        }

        static void ensureRegistered() { }
    }

    private static void registerAtomicCodecs() {
        AtomicCodecs.ensureRegistered();
    }

    /** Source: JDKAtomicTypesDeserTest#testAtomicBoolean. */
    @Test void testAtomicBoolean() { registerAtomicCodecs(); assertEquals(true, ((AtomicBoolean) binding(StreamingContext.EMPTY).readNode("true", AtomicBoolean.class)).get()); }
    /** Source: JDKAtomicTypesDeserTest#testAtomicInt. */
    @Test void testAtomicInt() { registerAtomicCodecs(); assertEquals(13, ((AtomicInteger) binding(StreamingContext.EMPTY).readNode("13", AtomicInteger.class)).get()); }
    /** Source: JDKAtomicTypesDeserTest#testAtomicLong. */
    @Test void testAtomicLong() { registerAtomicCodecs(); assertEquals(12345678901L, ((AtomicLong) binding(StreamingContext.EMPTY).readNode("12345678901", AtomicLong.class)).get()); }
    /** ValueCodec cannot recursively bind AtomicReference's generic long[] payload. */
    @Disabled("TODO: design generic payload binding for ValueCodec before supporting AtomicReference<long[]>.")
    @SuppressWarnings("unchecked")
    @Test void testAtomicReference() {
        AtomicReference<long[]> value = (AtomicReference<long[]>) binding(StreamingContext.EMPTY).readNode("[1,2]", new TypeReference<AtomicReference<long[]>>() {}.getType());
        assertArrayEquals(new long[] { 1, 2 }, value.get());
    }
}
