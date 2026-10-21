package org.sjf4j.meta;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;

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
    void moreThanFiveParametersKeepExistingHandleFallback() {
        CreatorInfo info = TypeRegistry.requireRegisteredPojoInfo(SixPrimitives.class).creatorInfo;
        assertNull(info.argsCreatorLambda1);
        assertNull(info.argsCreatorLambda5);
        SixPrimitives instance = (SixPrimitives) info.newPojoWithArgs(new Object[]{1, 2, 3, 4, 5, 6});
        assertEquals(1, instance.a);
        assertEquals(6, instance.f);
    }
}
