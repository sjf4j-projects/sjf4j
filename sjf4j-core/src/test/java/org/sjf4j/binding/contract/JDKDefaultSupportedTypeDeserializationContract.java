package org.sjf4j.binding.contract;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.StreamingBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** JDK types supplied by SJF4J's built-in value codecs. */
public abstract class JDKDefaultSupportedTypeDeserializationContract {
    protected abstract StreamingBinder<?, ?> binding(RuntimeContext context);
    /** SJF4J supplies Optional through its default value codec. */
    @Test void testOptionalUsesBuiltInCodec() {
        assertEquals(Optional.of("value"), binding(RuntimeContext.EMPTY).readNode("\"value\"", new TypeReference<Optional<String>>() {}.getType()));
    }
    /** SJF4J supplies Instant through its default ISO-8601 value codec. */
    @Test void testInstantUsesBuiltInCodec() {
        assertEquals(Instant.parse("2020-01-02T03:04:05Z"), binding(RuntimeContext.EMPTY).readNode("\"2020-01-02T03:04:05Z\"", Instant.class));
    }
}
