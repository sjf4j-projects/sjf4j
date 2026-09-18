package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Enum contracts structurally adapted from Jackson's EnumDeserializationTest. */
public abstract class EnumDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Structural source: EnumDeserializationTest#testSimple; retains named-value and null input only, not numeric or invalid-value branches. */
    @Test void testSimple() { assertEquals(TestEnum.OK, binding(StreamingContext.EMPTY).readNode("\"OK\"", TestEnum.class)); assertNull(binding(StreamingContext.EMPTY).readNode("null", TestEnum.class)); }
    /** Structural source: EnumDeserializationTest#testComplexEnum; reads a fixed enum token rather than Jackson's serialize-then-read sequence. */
    @Test void testComplexEnum() { assertEquals(java.util.concurrent.TimeUnit.SECONDS, binding(StreamingContext.EMPTY).readNode("\"SECONDS\"", java.util.concurrent.TimeUnit.class)); }
    enum TestEnum { JACKSON, RULES, OK }
}
