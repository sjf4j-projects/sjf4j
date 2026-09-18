package org.sjf4j.binding.access;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Default SJF4J unknown-property behavior structurally compared with Jackson cases. */
public abstract class UnknownPropertyDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);

    /** Structural source: UnknownPropertyDeserTest#testUnknownHandlingIgnoreWithFeature. */
    @Test void testUnknownHandlingIgnoreWithFeature() {
        Bean bean = (Bean) binding(StreamingContext.EMPTY).readNode("{\"a\":1,\"unknown\":[1,2,3],\"b\":-1}", Bean.class);
        assertEquals(1, bean.a); assertEquals(-1, bean.b);
    }

    /** Structural source: UnknownPropertyDeserTest#testClassWithIgnoreUnknown. */
    @Test void testClassWithIgnoreUnknown() {
        Bean bean = (Bean) binding(StreamingContext.EMPTY).readNode("{\"unknown\":{\"x\":1},\"a\":-3,\"after\":4}", Bean.class);
        assertEquals(-3, bean.a); assertEquals(4, bean.after);
    }

    /** Structural source: UnknownPropertyDeserTest#testIssue987; an unknown nested value is consumed before the next object is read. */
    @Test void testIssue987() {
        Bean[] beans = (Bean[]) binding(StreamingContext.EMPTY).readNode("[{\"a\":1,\"unknown\":{\"nested\":{}}},{\"a\":2,\"b\":3}]", Bean[].class);
        assertEquals(1, beans[0].a); assertEquals(2, beans[1].a); assertEquals(3, beans[1].b);
    }

    static class Bean { public int a; public int b; public int after; }
}
