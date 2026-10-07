package org.sjf4j.exception;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.Binder;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.path.PathSegment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
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
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromNode(null, nullClass, false));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromNode(null, nullType, false));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromProperties(new Properties(), nullClass));
        assertThrowsExactly(NullPointerException.class, () -> sjf4j.fromProperties(new Properties(), nullType));

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

    private static void assertWriteBoundaries(FailingBinder binder) {
        BindingException failure = (BindingException) binder.failure;
        assertFailure(failure, () -> binder.writeNode(new StringWriter(), new Object()));
        assertFailure(failure, () -> binder.writeNode(new ByteArrayOutputStream(), new Object()));
        assertFailure(failure, () -> binder.writeNodeAsString(new Object()));
        assertFailure(failure, () -> binder.writeNodeAsBytes(new Object()));
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

    private static final class FailingBinder extends Binder<StreamingReader, StreamingWriter> {
        private final RuntimeException failure;

        private FailingBinder(RuntimeException failure) {
            super(RuntimeContext.EMPTY);
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

}
