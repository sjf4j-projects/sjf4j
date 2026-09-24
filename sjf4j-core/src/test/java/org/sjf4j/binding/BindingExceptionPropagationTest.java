package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.NodeKind;
import org.sjf4j.binding.simple.SimpleJsonReader;
import org.sjf4j.binding.simple.SimpleJsonWriter;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.ContainerInfo;
import org.sjf4j.node.CreatorInfo;
import org.sjf4j.node.PojoAccess;
import org.sjf4j.path.PathSegment;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueInfo;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class BindingExceptionPropagationTest {

    @Test
    void valueCodecAndPojoAccessKeepPathBindingExceptions() throws Throwable {
        BindingException failure = failure();
        ValueInfo valueInfo = new ValueInfo("", String.class, String.class, new ThrowingCodec(failure),
                null, null, null);

        assertSame(failure, assertThrowsExactly(BindingException.class, () -> valueInfo.valueToRaw("value")));
        assertSame(failure, assertThrowsExactly(BindingException.class, () -> valueInfo.rawToValue("value")));
        assertSame(failure, assertThrowsExactly(BindingException.class, () -> valueInfo.valueCopy("value")));

        Accessor accessor = new Accessor(failure);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle getter = lookup.unreflect(Accessor.class.getMethod("getValue"));
        MethodHandle setter = lookup.unreflect(Accessor.class.getMethod("setValue", Object.class));

        assertSame(failure, assertThrowsExactly(BindingException.class,
                () -> PojoAccess.invokeGetter("value", getter, null, accessor)));
        assertSame(failure, assertThrowsExactly(BindingException.class,
                () -> PojoAccess.invokeSetter("value", setter, null, accessor, "value")));
    }

    @Test
    void creatorAndContainerHandlesKeepPathBindingExceptions() throws Throwable {
        BindingException failure = failure();
        creatorFailure = failure;
        containerFailure = failure;
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        CreatorInfo creator = new CreatorInfo(Object.class,
                lookup.findStatic(BindingExceptionPropagationTest.class, "throwCreator",
                        MethodType.methodType(Object.class)),
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        ContainerInfo container = new ContainerInfo(Object.class, NodeKind.ARRAY_LIST,
                lookup.findStatic(BindingExceptionPropagationTest.class, "throwContainer",
                        MethodType.methodType(Object.class)), null);

        assertSame(failure, assertThrowsExactly(BindingException.class, creator::newPojoNoArgs));
        assertSame(failure, assertThrowsExactly(BindingException.class, container::newContainer));
    }

    @Test
    void primitiveMethodHandleFallbacksKeepPathBindingExceptions() throws Throwable {
        BindingException failure = failure();
        PrimitiveAccessor accessor = new PrimitiveAccessor(failure);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle setter = lookup.unreflect(PrimitiveAccessor.class.getMethod("setValue", int.class));
        MethodHandle getter = lookup.unreflect(PrimitiveAccessor.class.getMethod("getValue"));

        FieldReader reader = FieldReader.create("value", int.class, Integer.class, false, null,
                setter, null, null, null);
        FieldWriter writer = FieldWriter.create("value", int.class, Integer.class, getter, null, null, null);

        assertSame(failure, assertThrowsExactly(BindingException.class,
                () -> reader.bind(new SimpleJsonReader(new StringReader("1")), accessor,
                        Object.class, PrimitiveAccessor.class, StreamingContext.EMPTY)));
        assertSame(failure, assertThrowsExactly(BindingException.class,
                () -> writer.write(new SimpleJsonWriter(null, new StringWriter()),
                        new PreparedName.SimplePreparedName("value"), accessor, StreamingContext.EMPTY, 0)));
    }

    public static Object throwCreator() {
        throw creatorFailure;
    }

    public static Object throwContainer() {
        throw containerFailure;
    }

    private static BindingException failure() {
        return new BindingException("failure", PathSegment.Root.INSTANCE);
    }

    private static BindingException creatorFailure;
    private static BindingException containerFailure;

    public static final class Accessor {
        private final BindingException failure;

        public Accessor(BindingException failure) {
            this.failure = failure;
        }

        public Object getValue() {
            throw failure;
        }

        public void setValue(Object value) {
            throw failure;
        }
    }

    public static final class PrimitiveAccessor {
        private final BindingException failure;

        public PrimitiveAccessor(BindingException failure) {
            this.failure = failure;
        }

        public int getValue() {
            throw failure;
        }

        public void setValue(int value) {
            throw failure;
        }
    }

    private static final class ThrowingCodec implements ValueCodec<String, String> {
        private final BindingException failure;

        private ThrowingCodec(BindingException failure) {
            this.failure = failure;
        }

        @Override
        public String valueToRaw(String value) {
            throw failure;
        }

        @Override
        public String rawToValue(String raw) {
            throw failure;
        }

        @Override
        public Class<String> valueClazz() {
            return String.class;
        }

        @Override
        public Class<String> rawClazz() {
            return String.class;
        }

        @Override
        public String valueCopy(String value) {
            throw failure;
        }
    }
}
