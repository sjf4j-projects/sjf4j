package org.sjf4j.binding.contract;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** JDK types supplied by SJF4J's built-in value codecs. */
public abstract class JDKDefaultSupportedTypeDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** SJF4J supplies Optional through its default value codec. */
    @Test void testOptionalUsesBuiltInCodec() {
        assertEquals(Optional.of("value"), binding(StreamingContext.EMPTY).readNode("\"value\"", new TypeReference<Optional<String>>() {}.getType()));
    }
    /** SJF4J supplies Instant through its default ISO-8601 value codec. */
    @Test void testInstantUsesBuiltInCodec() {
        assertEquals(Instant.parse("2020-01-02T03:04:05Z"), binding(StreamingContext.EMPTY).readNode("\"2020-01-02T03:04:05Z\"", Instant.class));
    }
}
