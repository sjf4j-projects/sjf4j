package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Retained SJF4J NodeValue dispatch contract; it is not Jackson-derived. */
public abstract class RetainedValueCodecDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);

    @Test void testBooleanCodecAtRoot() {
        assertEquals(true, ((BooleanCode) binding(StreamingContext.EMPTY).readNode("true", BooleanCode.class)).value);
    }

    @Test void testNumberCodecAtRoot() {
        assertEquals(7, ((NumberCode) binding(StreamingContext.EMPTY).readNode("7", NumberCode.class)).value.intValue());
    }

    @Test void testBooleanCodecAtField() {
        assertEquals(false, ((CodecHolder) binding(StreamingContext.EMPTY).readNode("{\"booleanCode\":false}", CodecHolder.class)).booleanCode.value);
    }

    @Test void testNumberCodecAtField() {
        assertEquals(8.5d, ((CodecHolder) binding(StreamingContext.EMPTY).readNode("{\"numberCode\":8.5}", CodecHolder.class)).numberCode.value.doubleValue());
    }

    @NodeValue static class BooleanCode {
        final boolean value;
        BooleanCode(boolean value) {
                this.value = value;
            }
        @ValueToRaw Boolean encode() {
                return value;
            }
        @RawToValue static BooleanCode decode(Boolean raw) {
                return new BooleanCode(raw);
            }
    }

    @NodeValue static class NumberCode {
        final Number value;
        NumberCode(Number value) {
                this.value = value;
            }
        @ValueToRaw Number encode() {
                return value;
            }
        @RawToValue static NumberCode decode(Number raw) {
                return new NumberCode(raw);
            }
    }

    static class CodecHolder { public BooleanCode booleanCode; public NumberCode numberCode; }
}
