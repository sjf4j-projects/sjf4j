package org.sjf4j.exception;

import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NodeExceptionSubclassTest {

    private static final Factory[] FACTORIES = {
            new Factory(NodeException.class, NodeException::new, NodeException::new, NodeException::new,
                    NodeException::new),
            new Factory(PathException.class, PathException::new, PathException::new, PathException::new,
                    PathException::new),
            new Factory(PatchException.class, PatchException::new, PatchException::new, PatchException::new,
                    PatchException::new),
            new Factory(MappingException.class, MappingException::new, MappingException::new, MappingException::new,
                    MappingException::new)
    };

    @Test
    void constructorsPreserveMessageAndCause() {
        Throwable cause = new IllegalStateException("cause");
        for (Factory factory : FACTORIES) {
            assertException(factory.type, factory.message.apply("message"), "message", null);
            assertException(factory.type, factory.messageAndCause.apply("message", cause), "message", cause);
            assertException(factory.type, factory.full.create("message", cause, false, false), "message", cause);
            assertException(factory.type, factory.cause.apply(cause), cause.toString(), cause);
        }
    }

    private static void assertException(Class<? extends NodeException> type, NodeException exception,
                                        String message, Throwable cause) {
        assertInstanceOf(type, exception);
        assertInstanceOf(NodeException.class, exception);
        assertEquals(message, exception.getMessage());
        if (cause == null) assertNull(exception.getCause());
        else assertSame(cause, exception.getCause());
    }

    private interface FullConstructor {
        NodeException create(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace);
    }

    private static final class Factory {
        final Class<? extends NodeException> type;
        final Function<String, NodeException> message;
        final BiFunction<String, Throwable, NodeException> messageAndCause;
        final FullConstructor full;
        final Function<Throwable, NodeException> cause;

        Factory(Class<? extends NodeException> type, Function<String, NodeException> message,
                BiFunction<String, Throwable, NodeException> messageAndCause, FullConstructor full,
                Function<Throwable, NodeException> cause) {
            this.type = type;
            this.message = message;
            this.messageAndCause = messageAndCause;
            this.full = full;
            this.cause = cause;
        }
    }

}
