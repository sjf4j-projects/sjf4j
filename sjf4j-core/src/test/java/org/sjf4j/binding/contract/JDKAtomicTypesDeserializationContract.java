package org.sjf4j.binding.contract;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Default atomic-value bindings from Jackson's JDKAtomicTypesDeserTest. */
public abstract class JDKAtomicTypesDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Source: JDKAtomicTypesDeserTest#testAtomicBoolean. */
    @Test void testAtomicBoolean() { assertEquals(true, ((AtomicBoolean) binding(StreamingContext.EMPTY).readNode("true", AtomicBoolean.class)).get()); }
    /** Source: JDKAtomicTypesDeserTest#testAtomicInt. */
    @Test void testAtomicInt() { assertEquals(13, ((AtomicInteger) binding(StreamingContext.EMPTY).readNode("13", AtomicInteger.class)).get()); }
    /** Source: JDKAtomicTypesDeserTest#testAtomicLong. */
    @Test void testAtomicLong() { assertEquals(12345678901L, ((AtomicLong) binding(StreamingContext.EMPTY).readNode("12345678901", AtomicLong.class)).get()); }
    /** Source: JDKAtomicTypesDeserTest#testAtomicReference. */
    @Test void testAtomicReference() {
        AtomicReference<long[]> value = (AtomicReference<long[]>) binding(StreamingContext.EMPTY).readNode("[1,2]", new TypeReference<AtomicReference<long[]>>() {}.getType());
        assertArrayEquals(new long[] { 1, 2 }, value.get());
    }
}
