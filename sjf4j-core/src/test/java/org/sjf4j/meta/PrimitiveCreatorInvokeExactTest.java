package org.sjf4j.meta;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.util.Invokers;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Directly verifies MethodHandle Object-to-primitive adaptation independent of
 * the JSON parser, including primitive forms not exercised in Creek.
 */
class PrimitiveCreatorInvokeExactTest {
    public static final class AllPrimitives {
        final byte b;
        final short s;
        final char c;
        final boolean active;
        final long number;

        @NodeCreator
        public AllPrimitives(@NodeProperty("b") byte b, @NodeProperty("s") short s,
                             @NodeProperty("c") char c, @NodeProperty("active") boolean active,
                             @NodeProperty("number") long number) {
            this.b = b;
            this.s = s;
            this.c = c;
            this.active = active;
            this.number = number;
        }
    }

    public static final class SixPrimitives {
        final int a, b, c, d, e, f;

        @NodeCreator
        public SixPrimitives(@NodeProperty("a") int a, @NodeProperty("b") int b,
                             @NodeProperty("c") int c, @NodeProperty("d") int d,
                             @NodeProperty("e") int e, @NodeProperty("f") int f) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
        }
    }

    public static final class SevenMixedFactory {
        final String name;
        final int id;
        final double value;
        final boolean enabled;
        final long count;
        final float ratio;
        final String note;

        private SevenMixedFactory(String name, int id, double value, boolean enabled,
                                  long count, float ratio, String note) {
            this.name = name;
            this.id = id;
            this.value = value;
            this.enabled = enabled;
            this.count = count;
            this.ratio = ratio;
            this.note = note;
        }

        @NodeCreator
        public static SevenMixedFactory create(@NodeProperty("name") String name,
                                               @NodeProperty("id") int id,
                                               @NodeProperty("value") double value,
                                               @NodeProperty("enabled") boolean enabled,
                                               @NodeProperty("count") long count,
                                               @NodeProperty("ratio") float ratio,
                                               @NodeProperty("note") String note) {
            return new SevenMixedFactory(name, id, value, enabled, count, ratio, note);
        }
    }

    @Test
    void supportsFiveDifferentPrimitiveTypesWithoutSpecialFunctions() {
        CreatorInfo info = TypeRegistry.requireRegisteredPojoInfo(AllPrimitives.class).creatorInfo;
        assertNotNull(info.argsCreatorLambda5);

        AllPrimitives value = (AllPrimitives) info.newPojoWithArgs(
                new Object[]{(byte) 3, (short) 500, 'X', true, 100_000_000_000L});
        assertEquals((byte) 3, value.b);
        assertEquals((short) 500, value.s);
        assertEquals('X', value.c);
        assertTrue(value.active);
        assertEquals(100_000_000_000L, value.number);

        AllPrimitives missing = (AllPrimitives) info.newPojoWithArgs(
                new Object[]{null, null, null, null, null});
        assertEquals((byte) 0, missing.b);
        assertEquals((short) 0, missing.s);
        assertEquals('\0', missing.c);
        assertFalse(missing.active);
        assertEquals(0L, missing.number);
    }

    @Test
    void moreThanFiveParametersUseCachedSpreaderWithPrimitiveDefaults() throws Throwable {
        CreatorInfo info = TypeRegistry.requireRegisteredPojoInfo(SixPrimitives.class).creatorInfo;
        assertNull(info.argsCreatorLambda1);
        assertNull(info.argsCreatorLambda5);
        MethodHandle spreader = Invokers.createArgsCreatorSpreader(info.argsCreatorHandle);
        assertNotNull(spreader);
        assertEquals(MethodType.methodType(Object.class, Object[].class), spreader.type());
        Object[] args = new Object[]{1, 2, 3, 4, 5, 6};
        SixPrimitives instance = (SixPrimitives) info.newPojoWithArgs(args.clone());
        assertEquals(1, instance.a);
        assertEquals(6, instance.f);

        SixPrimitives direct = (SixPrimitives) spreader.invokeExact(args);
        assertEquals(instance.a, direct.a);
        assertEquals(instance.f, direct.f);

        SixPrimitives missing = (SixPrimitives) info.newPojoWithArgs(
                new Object[]{1, null, 3, null, 5, null});
        assertEquals(0, missing.b);
        assertEquals(0, missing.d);
        assertEquals(0, missing.f);
    }

    @Test
    void sevenMixedFactoryUsesSameGenericArraySpreader() {
        CreatorInfo info = TypeRegistry.requireRegisteredPojoInfo(SevenMixedFactory.class).creatorInfo;
        assertNotNull(Invokers.createArgsCreatorSpreader(info.argsCreatorHandle));

        SevenMixedFactory value = (SevenMixedFactory) info.newPojoWithArgs(
                new Object[]{"factory", 7, 12.5, true, 1234L, 1.25f, "note"});
        assertEquals("factory", value.name);
        assertEquals(7, value.id);
        assertEquals(12.5, value.value);
        assertTrue(value.enabled);
        assertEquals(1234L, value.count);
        assertEquals(1.25f, value.ratio);
        assertEquals("note", value.note);

        SevenMixedFactory missing = (SevenMixedFactory) info.newPojoWithArgs(
                new Object[]{null, null, null, null, null, null, null});
        assertNull(missing.name);
        assertEquals(0, missing.id);
        assertEquals(0.0, missing.value);
        assertFalse(missing.enabled);
        assertEquals(0L, missing.count);
        assertEquals(0f, missing.ratio);
        assertNull(missing.note);
    }
}
