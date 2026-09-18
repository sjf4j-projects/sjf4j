package org.sjf4j.binding.creator;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.exception.BindingException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelegatingCreatorDeserializationContract {
    private final SimpleJsonBinder binding = new SimpleJsonBinder(StreamingContext.EMPTY);

    // Structural source: DelegatingCreatorsTest#testIntegerDelegate; NodeCreator has no delegating mode.
    @Test void delegatingIntegerNodeCreatorIsRejectedForScalar() {
        assertThrows(BindingException.class, () -> binding.readNode("13", IntegerValue.class));
    }

    // Structural source: DelegatingArrayCreatorsTest#testDelegatingArray1804; NodeCreator has no delegating mode.
    @Test void delegatingListCreatorReceivesArray() {
        assertNotNull(binding.readNode("[]", ListValue.class));
    }

    // Structural source: DelegatingCreatorsTest#testIssue465; NodeCreator has no delegating mode.
    @Test void delegatingMapNodeCreatorIsRejectedForObject() {
        assertThrows(BindingException.class, () -> binding.readNode("{\"A\":12}", MapValue.class));
    }

    static class IntegerValue { final int value; @NodeCreator IntegerValue(int value) { this.value = value; } }

    @NodeValue
    abstract static class ListValue {
        final List<Integer> value;
        ListValue(List<Integer> value) { this.value = value; }
        @RawToValue
        static ListValue of(List<Integer> value) { return new ListValue(value) { }; }

        @ValueToRaw
        List<Integer> toRaw() {
            return value;
        }
    }
    static class MapValue { final Map<String, Long> value; @NodeCreator MapValue(Map<String, Long> value) { this.value = value; } }
}
