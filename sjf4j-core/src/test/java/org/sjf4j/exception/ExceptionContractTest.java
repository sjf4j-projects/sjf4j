package org.sjf4j.exception;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.binding.simple.SimpleNodeBinder;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.facade.simple.SimpleNodeFacade;
import org.sjf4j.path.PathSegment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionContractTest {

    @Test
    void bindingExceptionConstructorsKeepCausePathAndThrowableFeatures() {
        Throwable cause = new IllegalStateException("cause");
        PathSegment path = PathSegment.Root.INSTANCE;
        BindingException[] errors = {
                new BindingException("message"),
                new BindingException("message", cause),
                new BindingException(cause),
                new BindingException("message", path),
                new BindingException("message", path, cause),
                new BindingException(cause, path)
        };

        assertException(errors[0], "message", null, null);
        assertException(errors[1], "message", cause, null);
        assertException(errors[2], cause.toString(), cause, null);
        assertException(errors[3], "message, at path '$'", null, path);
        assertException(errors[4], "message, at path '$'", cause, path);
        assertException(errors[5], cause.toString() + ", at path '$'", cause, path);
    }

    @Test
    void streamingBinderPreservesPathBindingExceptionsAtEveryBoundary() {
        assertReadBoundaries(new FailingBinder(new BindingException("read", PathSegment.Root.INSTANCE)));
        assertWriteBoundaries(new FailingBinder(new BindingException("write", PathSegment.Root.INSTANCE)));
    }

    @Test
    void streamingFacadePreservesPathBindingExceptionsAtEveryBoundary() {
        assertReadBoundaries(new FailingFacade(new BindingException("read", PathSegment.Root.INSTANCE)));
        assertWriteBoundaries(new FailingFacade(new BindingException("write", PathSegment.Root.INSTANCE)));
    }

    @Test
    void jsonFacadeModesPreservePathBindingExceptionsAtEveryBoundary() {
        for (org.sjf4j.facade.StreamingContext.StreamingMode mode :
                new org.sjf4j.facade.StreamingContext.StreamingMode[] {
                        org.sjf4j.facade.StreamingContext.StreamingMode.EXCLUSIVE_IO,
                        org.sjf4j.facade.StreamingContext.StreamingMode.PLUGIN_MODULE
                }) {
            assertReadBoundaries(new FailingFacade(new BindingException("read", PathSegment.Root.INSTANCE), mode));
            assertWriteBoundaries(new FailingFacade(new BindingException("write", PathSegment.Root.INSTANCE), mode));
        }
    }

    @Test
    void nonBindingFailuresAreWrappedAndNativeBindingCausesKeepTheirPath() {
        FailingBinder binder = new FailingBinder(new IllegalStateException("read"));
        BindingException wrapped = assertThrowsExactly(BindingException.class,
                () -> binder.readNode("null", Object.class));
        assertEquals("failed to read streaming into node of 'class java.lang.Object'", wrapped.getMessage());
        assertEquals("read", wrapped.getCause().getMessage());

        FailingFacade facade = new FailingFacade(new BindingException("unused"));
        IllegalStateException cause = new IllegalStateException("native");
        BindingException nativeFailure = assertThrowsExactly(BindingException.class,
                () -> facade.failedToRead(Object.class, cause));
        assertSame(cause, nativeFailure.getCause());

        BindingException pathFailure = new BindingException("nested", PathSegment.Root.INSTANCE);
        IllegalStateException nestedRead = new IllegalStateException(pathFailure);
        BindingException contextual = assertThrowsExactly(BindingException.class,
                () -> facade.failedToRead(Object.class, nestedRead));
        assertSame(pathFailure.getPathSegment(), contextual.getPathSegment());
        assertSame(nestedRead, contextual.getCause());
        assertTrue(contextual.getMessage().contains("at path '$'"));
        assertSame(pathFailure, assertThrowsExactly(BindingException.class,
                () -> facade.failedToRead(Object.class, pathFailure)));

        IllegalStateException nestedWrite = new IllegalStateException(pathFailure);
        BindingException writeContext = assertThrowsExactly(BindingException.class,
                () -> facade.failedToWrite(new Object(), nestedWrite));
        assertSame(pathFailure.getPathSegment(), writeContext.getPathSegment());
        assertSame(nestedWrite, writeContext.getCause());
        assertSame(pathFailure, assertThrowsExactly(BindingException.class,
                () -> facade.failedToWrite(new Object(), pathFailure)));
    }

    @Test
    void typedTargetsRejectNullBeforeBindingOrNullInputValues() {
        Sjf4j sjf4j = Sjf4j.global();
        Class<String> nullClass = null;
        TypeReference<String> nullType = null;
        byte[] jsonBytes = "null".getBytes(StandardCharsets.UTF_8);

        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson(new StringReader("null"), nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson(new StringReader("null"), nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson("null", nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson("null", nullType));
        assertThrowsExactly(NullPointerException.class,
                () -> sjf4j.fromJson(new ByteArrayInputStream(jsonBytes), nullClass));
        assertThrowsExactly(NullPointerException.class,
                () -> sjf4j.fromJson(new ByteArrayInputStream(jsonBytes), nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson(jsonBytes, nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromJson(jsonBytes, nullType));

        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromYaml(new StringReader("null"), nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromYaml(new StringReader("null"), nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromYaml("null", nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromYaml("null", nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromNode(null, nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromNode(null, nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.bindNode(null, nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.bindNode(null, nullType));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromProperties(new Properties(), nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromProperties(new Properties(), nullType));

        Type nullTarget = null;
        SimpleJsonFacade facade = new SimpleJsonFacade();
        SimpleJsonBinder binder = new SimpleJsonBinder(StreamingContext.EMPTY);
        assertThrowsExactly(NullPointerException.class, () -> facade.readNode(new StringReader("null"), nullTarget));
        assertThrowsExactly(NullPointerException.class,
                () -> facade.readNode(new ByteArrayInputStream(jsonBytes), nullTarget));
        assertThrowsExactly(NullPointerException.class, () -> facade.readNode("null", nullTarget));
        assertThrowsExactly(NullPointerException.class, () -> facade.readNode(jsonBytes, nullTarget));
        assertThrowsExactly(NullPointerException.class, () -> binder.readNode(new StringReader("null"), nullTarget));
        assertThrowsExactly(NullPointerException.class,
                () -> binder.readNode(new ByteArrayInputStream(jsonBytes), nullTarget));
        assertThrowsExactly(NullPointerException.class, () -> binder.readNode("null", nullTarget));
        assertThrowsExactly(NullPointerException.class, () -> binder.readNode(jsonBytes, nullTarget));
        assertThrowsExactly(NullPointerException.class,
                () -> new SimpleNodeFacade().readNode(new ThrowingGetter(), nullTarget, false));
        assertThrowsExactly(NullPointerException.class, () -> new SimpleNodeBinder().readNode(null, nullTarget, false));
        assertThrowsExactly(NullPointerException.class,
                () -> new FailingBinder(new BindingException("reader")).readNode("null", nullTarget));
        assertThrowsExactly(NullPointerException.class,
                () -> new FailingFacade(new BindingException("reader")).readNode("null", nullTarget));
    }

    private static void assertException(BindingException error, String message, Throwable cause, PathSegment path) {
        assertEquals(message, error.getMessage());
        if (cause == null) assertNull(error.getCause());
        else assertSame(cause, error.getCause());
        assertSame(path, error.getPathSegment());
        error.addSuppressed(new IllegalArgumentException("suppressed"));
        assertEquals(1, error.getSuppressed().length);
        assertTrue(error.getStackTrace().length > 0);
    }

    private static void assertReadBoundaries(FailingBinder binder) {
        BindingException failure = (BindingException) binder.failure;
        assertFailure(failure, () -> binder.readNode(new StringReader("null"), Object.class));
        assertFailure(failure, () -> binder.readNode(new ByteArrayInputStream(new byte[0]), Object.class));
        assertFailure(failure, () -> binder.readNode("null", Object.class));
        assertFailure(failure, () -> binder.readNode(new byte[0], Object.class));
    }

    private static void assertReadBoundaries(FailingFacade facade) {
        BindingException failure = (BindingException) facade.failure;
        assertFailure(failure, () -> facade.readNode(new StringReader("null"), Object.class));
        assertFailure(failure, () -> facade.readNode(new ByteArrayInputStream(new byte[0]), Object.class));
        assertFailure(failure, () -> facade.readNode("null", Object.class));
        assertFailure(failure, () -> facade.readNode(new byte[0], Object.class));
    }

    private static void assertWriteBoundaries(FailingBinder binder) {
        BindingException failure = (BindingException) binder.failure;
        assertFailure(failure, () -> binder.writeNode(new StringWriter(), new Object()));
        assertFailure(failure, () -> binder.writeNode(new ByteArrayOutputStream(), new Object()));
        assertFailure(failure, () -> binder.writeNodeAsString(new Object()));
        assertFailure(failure, () -> binder.writeNodeAsBytes(new Object()));
    }

    private static void assertWriteBoundaries(FailingFacade facade) {
        BindingException failure = (BindingException) facade.failure;
        assertFailure(failure, () -> facade.writeNode(new StringWriter(), new Object()));
        assertFailure(failure, () -> facade.writeNode(new ByteArrayOutputStream(), new Object()));
        assertFailure(failure, () -> facade.writeNodeAsString(new Object()));
        assertFailure(failure, () -> facade.writeNodeAsBytes(new Object()));
    }

    private static void assertFailure(BindingException expected, ThrowingOperation operation) {
        BindingException actual = assertThrowsExactly(BindingException.class, operation::run);
        assertSame(expected, actual);
        assertNull(actual.getCause());
        assertSame(PathSegment.Root.INSTANCE, actual.getPathSegment());
    }

    private interface ThrowingOperation {
        void run();
    }

    private static final class ThrowingGetter {
        public String getValue() {
            throw new AssertionError("getter must not run");
        }
    }

    private static final class FailingBinder extends StreamingBinder<StreamingReader, StreamingWriter> {
        private final RuntimeException failure;

        private FailingBinder(RuntimeException failure) {
            super(StreamingContext.EMPTY);
            this.failure = failure;
        }

        @Override
        public StreamingReader createReader(Reader input) {
            throw failure;
        }

        @Override
        public StreamingWriter createWriter(Writer output) {
            throw failure;
        }
    }

    private static final class FailingFacade implements JsonFacade<org.sjf4j.facade.StreamingReader,
            org.sjf4j.facade.StreamingWriter> {
        private final RuntimeException failure;
        private final org.sjf4j.facade.StreamingContext.StreamingMode mode;

        private FailingFacade(RuntimeException failure) {
            this(failure, org.sjf4j.facade.StreamingContext.StreamingMode.SHARED_IO);
        }

        private FailingFacade(RuntimeException failure,
                              org.sjf4j.facade.StreamingContext.StreamingMode mode) {
            this.failure = failure;
            this.mode = mode;
        }

        @Override
        public org.sjf4j.facade.StreamingContext.StreamingMode realStreamingMode() {
            return mode;
        }

        @Override
        public org.sjf4j.facade.StreamingReader createReader(Reader input) {
            throw failure;
        }

        @Override
        public org.sjf4j.facade.StreamingWriter createWriter(Writer output) {
            throw failure;
        }

        @Override
        public Object readNodeExclusive(Reader input, Type type) {
            throw failure;
        }

        @Override
        public Object readNodePlugin(Reader input, Type type) {
            throw failure;
        }

        @Override
        public void writeNodeExclusive(Writer output, Object node) {
            throw failure;
        }

        @Override
        public void writeNodePlugin(Writer output, Object node) {
            throw failure;
        }
    }
}
