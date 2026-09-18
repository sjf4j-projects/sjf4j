package org.sjf4j.binding.contract;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Types Jackson's unconfigured ObjectMapper rejects; these are failure contracts, not omissions. */
public abstract class JDKDefaultUnsupportedTypeDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Jackson default result verified against ObjectMapper: Optional needs jackson-datatype-jdk8. */
    @Test void testOptionalDefaultFailure() {
        assertThrows(RuntimeException.class, () -> binding(StreamingContext.EMPTY).readNode("\"value\"", new TypeReference<Optional<String>>() {}.getType()));
    }
    /** Jackson default result verified against ObjectMapper: Instant needs jackson-datatype-jsr310. */
    @Test void testJavaTimeDefaultFailure() {
        assertThrows(RuntimeException.class, () -> binding(StreamingContext.EMPTY).readNode("\"2020-01-02T03:04:05Z\"", Instant.class));
    }
}
