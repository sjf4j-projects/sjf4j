package org.sjf4j.binding.contract;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.Binder;
import org.sjf4j.value.ValueRegistry;
import org.sjf4j.value.ValueCodec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** User-registered non-native atomic-value bindings via SJF4J's ValueCodec extension API. */
public abstract class JDKAtomicTypesDeserializationContract {
    protected abstract Binder<?, ?> binding(RuntimeContext context);
    private static final class AtomicCodecs {
        static {
            ValueRegistry.registerByCodec(new ValueCodec.SimpleValueCodec<>(
                    AtomicBoolean.class, Boolean.class, AtomicBoolean::get, AtomicBoolean::new), null, false);
        }

        static void ensureRegistered() { }
    }

    private static void registerAtomicCodecs() {
        AtomicCodecs.ensureRegistered();
    }

    /** Source: JDKAtomicTypesDeserTest#testAtomicBoolean. */
    @Test void testAtomicBoolean() {
            registerAtomicCodecs(); assertEquals(true, ((AtomicBoolean) binding(RuntimeContext.EMPTY).readNode("true", AtomicBoolean.class)).get());
        }
    /** ValueCodec cannot recursively bind AtomicReference's generic long[] payload. */
    @Disabled("TODO: design generic payload binding for ValueCodec before supporting AtomicReference<long[]>.")
    @SuppressWarnings("unchecked")
    @Test void testAtomicReference() {
        AtomicReference<long[]> value = (AtomicReference<long[]>) binding(RuntimeContext.EMPTY).readNode("[1,2]", new TypeReference<AtomicReference<long[]>>() {}.getType());
        assertArrayEquals(new long[] { 1, 2 }, value.get());
    }
}
