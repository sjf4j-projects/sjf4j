package org.sjf4j.binding.contract;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.Binder;
import org.sjf4j.exception.BindingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** JDK binding behavior covered by SJF4J's defaults. */
public abstract class JDKDefaultSupportedTypeDeserializationContract {
    protected abstract Binder<?, ?> binding(RuntimeContext context);

    @Test void testOptionalIsUnsupported() {
        assertThrows(BindingException.class, () -> binding(RuntimeContext.EMPTY)
                .readNode("null", new TypeReference<Optional<String>>() {}.getType()));
    }
    /** SJF4J supplies Instant through its default ISO-8601 value codec. */
    @Test void testInstantUsesBuiltInCodec() {
        assertEquals(Instant.parse("2020-01-02T03:04:05Z"), binding(RuntimeContext.EMPTY).readNode("\"2020-01-02T03:04:05Z\"", Instant.class));
    }
}
