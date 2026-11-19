package org.sjf4j.binding.contract;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.Binder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** JDK binding behavior covered by SJF4J's defaults. */
public abstract class JDKDefaultSupportedTypeDeserializationContract {
    protected abstract Binder<?, ?> binding(NodeConfig config);

    /** SJF4J supplies Instant through its default ISO-8601 value codec. */
    @Test void testInstantUsesBuiltInCodec() {
        assertEquals(Instant.parse("2020-01-02T03:04:05Z"), binding(NodeConfig.DEFAULT).readNode("\"2020-01-02T03:04:05Z\"", Instant.class));
    }
}
