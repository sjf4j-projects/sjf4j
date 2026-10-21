package org.sjf4j.testbench.processor;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.TypeRegistry;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the generic fixed-arity Creator adapter with mixed primitive and
 * reference parameters, without introducing primitive-specific Func fields.
 */
class RuntimePrimitiveCreatorAdapterTest {
    static Stream<Arguments> backends() {
        return Stream.of(
                Arguments.of("simple", new SimpleJsonBinder()),
                Arguments.of("jackson2", new Jackson2Binder()),
                Arguments.of("jackson3", new Jackson3Binder()),
                Arguments.of("fastjson2", new Fastjson2Binder()),
                Arguments.of("gson", new GsonBinder()),
                Arguments.of("jsonp", new JsonpBinder()));
    }

    public static final class One {
        public final double value;

        @NodeCreator
        public One(@NodeProperty("value") double value) {
            this.value = value;
        }
    }

    public static final class Two {
        public final int id;
        public final String name;

        @NodeCreator
        public Two(@NodeProperty("id") int id, @NodeProperty("name") String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Three {
        public final String name;
        public final double value;
        public final boolean active;

        @NodeCreator
        public Three(@NodeProperty("name") String name,
                     @NodeProperty("value") double value,
                     @NodeProperty("active") boolean active) {
            this.name = name;
            this.value = value;
            this.active = active;
        }
    }

    public static final class Four {
        public final long count;
        public final int id;
        public final String name;
        public final float ratio;

        @NodeCreator
        public Four(@NodeProperty("count") long count,
                    @NodeProperty("id") int id,
                    @NodeProperty("name") String name,
                    @NodeProperty("ratio") float ratio) {
            this.count = count;
            this.id = id;
            this.name = name;
            this.ratio = ratio;
        }
    }

    public static final class Five {
        public final String name;
        public final int id;
        public final double value;
        public final boolean active;
        public final long count;

        @NodeCreator
        public Five(@NodeProperty("name") String name,
                    @NodeProperty("id") int id,
                    @NodeProperty("value") double value,
                    @NodeProperty("active") boolean active,
                    @NodeProperty("count") long count) {
            this.name = name;
            this.id = id;
            this.value = value;
            this.active = active;
            this.count = count;
        }
    }

    public record MixedRecord(int id, String name, double value) {}

    public static final class Factory {
        public final int id;
        public final String name;
        public final double value;

        private Factory(int id, String name, double value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        @NodeCreator
        public static Factory of(@NodeProperty("id") int id,
                                 @NodeProperty("name") String name,
                                 @NodeProperty("value") double value) {
            return new Factory(id, name, value);
        }
    }

    public static final class Failing {
        @NodeCreator
        public Failing(@NodeProperty("id") int id) {
            throw new IllegalArgumentException("creator failed");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void mixedConstructorsUseFixedArityFunctions(String backend, Binder<?, ?> binder) {
        CreatorInfo oneInfo = TypeRegistry.requireRegisteredPojoInfo(One.class).creatorInfo;
        assertNotNull(oneInfo.argsCreatorLambda1, backend);
        One one = (One) binder.readNode("{\"value\":12.5}", One.class);
        assertEquals(12.5d, one.value, backend);

        CreatorInfo twoInfo = TypeRegistry.requireRegisteredPojoInfo(Two.class).creatorInfo;
        assertNotNull(twoInfo.argsCreatorLambda2, backend);
        Two two = (Two) binder.readNode("{\"name\":\"Ada\",\"id\":7}", Two.class);
        assertEquals(7, two.id, backend);
        assertEquals("Ada", two.name, backend);

        CreatorInfo threeInfo = TypeRegistry.requireRegisteredPojoInfo(Three.class).creatorInfo;
        assertNotNull(threeInfo.argsCreatorLambda3, backend);
        Three three = (Three) binder.readNode(
                "{\"active\":true,\"value\":0.125,\"name\":\"T\"}", Three.class);
        assertEquals("T", three.name, backend);
        assertEquals(0.125d, three.value, backend);
        assertTrue(three.active, backend);

        CreatorInfo fourInfo = TypeRegistry.requireRegisteredPojoInfo(Four.class).creatorInfo;
        assertNotNull(fourInfo.argsCreatorLambda4, backend);
        Four four = (Four) binder.readNode(
                "{\"ratio\":1.25,\"name\":\"F\",\"id\":3,\"count\":2147483649}", Four.class);
        assertEquals(2147483649L, four.count, backend);
        assertEquals(3, four.id, backend);
        assertEquals("F", four.name, backend);
        assertEquals(1.25f, four.ratio, backend);

        CreatorInfo fiveInfo = TypeRegistry.requireRegisteredPojoInfo(Five.class).creatorInfo;
        assertNotNull(fiveInfo.argsCreatorLambda5, backend);
        Five five = (Five) binder.readNode(
                "{\"count\":12,\"active\":true,\"value\":2.5,\"id\":9,\"name\":\"Five\"}",
                Five.class);
        assertEquals("Five", five.name, backend);
        assertEquals(9, five.id, backend);
        assertEquals(2.5d, five.value, backend);
        assertTrue(five.active, backend);
        assertEquals(12L, five.count, backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void missingAndNullPrimitiveValuesKeepDefaults(String backend, Binder<?, ?> binder) {
        One absent = (One) binder.readNode("{}", One.class);
        One explicitNull = (One) binder.readNode("{\"value\":null}", One.class);
        assertEquals(0.0d, absent.value, backend);
        assertEquals(0.0d, explicitNull.value, backend);

        Five partial = (Five) binder.readNode("{\"name\":\"partial\",\"id\":null}", Five.class);
        assertEquals("partial", partial.name, backend);
        assertEquals(0, partial.id, backend);
        assertEquals(0.0d, partial.value, backend);
        assertFalse(partial.active, backend);
        assertEquals(0L, partial.count, backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void recordsAndFactoryCreatorsRemainSupported(String backend, Binder<?, ?> binder) {
        CreatorInfo factory = TypeRegistry.requireRegisteredPojoInfo(Factory.class).creatorInfo;
        assertNotNull(factory.argsCreatorLambda3, backend);
        Factory result = (Factory) binder.readNode("{\"id\":4,\"name\":\"factory\",\"value\":3.25}", Factory.class);
        assertEquals(4, result.id, backend);
        assertEquals("factory", result.name, backend);
        assertEquals(3.25, result.value, backend);

        CreatorInfo record = TypeRegistry.requireRegisteredPojoInfo(MixedRecord.class).creatorInfo;
        assertNotNull(record.argsCreatorLambda3, backend);
        MixedRecord recordValue = (MixedRecord) binder.readNode(
                "{\"id\":2,\"name\":\"record\",\"value\":4.5}", MixedRecord.class);
        assertEquals(new MixedRecord(2, "record", 4.5), recordValue, backend);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void creatorExceptionsStillGetBindingException(String backend, Binder<?, ?> binder) {
        BindingException failure = assertThrows(BindingException.class,
                () -> binder.readNode("{\"id\":7}", Failing.class), backend);
        assertInstanceOf(IllegalArgumentException.class, failure.getCause(), backend);
    }
}
