package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.exception.BindingException;
import org.sjf4j.path.PathSegment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class BinderExceptionPropagationTest {
    @Test
    void streamingBinderPreservesPathBindingExceptionsAtEveryBoundary() {
        assertReadBoundaries(new FailingBinder(new BindingException("read", PathSegment.Root.INSTANCE)));
        assertWriteBoundaries(new FailingBinder(new BindingException("write", PathSegment.Root.INSTANCE)));
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

    private interface ThrowingOperation { void run(); }

    private static final class FailingBinder extends Binder<StreamingReader, StreamingWriter> {
        private final RuntimeException failure;

        private FailingBinder(RuntimeException failure) {
            super(RuntimeContext.EMPTY);
            this.failure = failure;
        }

        @Override
        public StreamingReader createReader(Reader input) { throw failure; }

        @Override
        public StreamingWriter createWriter(Writer output) { throw failure; }
    }
}
