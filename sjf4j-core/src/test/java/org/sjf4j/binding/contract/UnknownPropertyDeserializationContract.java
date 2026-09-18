package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Default SJF4J unknown-property behavior structurally compared with Jackson cases. */
public abstract class UnknownPropertyDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Structural source: UnknownPropertyDeserTest#testUnknownHandlingIgnoreWithFeature; SJF4J ignores unknown properties without a feature toggle. */
    @Test void testUnknownHandlingIgnoreWithFeature() {
        Bean bean = (Bean) binding(StreamingContext.EMPTY).readNode("{\"a\":1,\"unknown\":[1,2,3],\"b\":-1}", Bean.class);
        assertEquals(1, bean.a); assertEquals(-1, bean.b);
    }
    /** Structural source: UnknownPropertyDeserTest#testClassWithIgnoreUnknown; SJF4J has no class-level ignore annotation. */
    @Test void testClassWithIgnoreUnknown() {
        Bean bean = (Bean) binding(StreamingContext.EMPTY).readNode("{\"unknown\":{\"x\":1},\"a\":-3,\"after\":4}", Bean.class);
        assertEquals(-3, bean.a); assertEquals(4, bean.after);
    }
    static class Bean { public int a; public int b; public int after; }
}
