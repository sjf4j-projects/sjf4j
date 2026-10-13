package org.sjf4j.exception;

import org.junit.jupiter.api.Test;
import org.sjf4j.path.PathSegment;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingExceptionTest {

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

    private static void assertException(BindingException error, String message, Throwable cause, PathSegment path) {
        assertEquals(message, error.getMessage());
        if (cause == null) assertNull(error.getCause());
        else assertSame(cause, error.getCause());
        assertSame(path, error.getPathSegment());
        error.addSuppressed(new IllegalArgumentException("suppressed"));
        assertEquals(1, error.getSuppressed().length);
        assertTrue(error.getStackTrace().length > 0);
    }

}
