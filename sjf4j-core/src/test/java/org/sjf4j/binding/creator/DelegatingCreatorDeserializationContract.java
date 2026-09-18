package org.sjf4j.binding.creator;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DelegatingCreatorDeserializationContract {
    private final SimpleJsonBinder binding = new SimpleJsonBinder(StreamingContext.EMPTY);

    // Structural source: DelegatingCreatorsTest#testIntegerDelegate; NodeCreator has no delegating mode.
    @Test void delegatingIntegerCreatorReceivesScalar() {
        assertEquals(13, ((IntegerValue) binding.readNode("13", IntegerValue.class)).value);
    }

    // Structural source: DelegatingArrayCreatorsTest#testDelegatingArray1804; NodeCreator has no delegating mode.
    @Test void delegatingListCreatorReceivesArray() {
        assertNotNull(binding.readNode("[]", ListValue.class));
    }

    // Structural source: DelegatingCreatorsTest#testIssue465; NodeCreator has no delegating mode.
    @Test void delegatingMapCreatorReceivesObject() {
        assertEquals(Long.valueOf(12), ((MapValue) binding.readNode("{\"A\":12}", MapValue.class)).value.get("A"));
    }

    static class IntegerValue { final int value; @NodeCreator IntegerValue(int value) { this.value = value; } }
    abstract static class ListValue {
        final List<Integer> value;
        ListValue(List<Integer> value) { this.value = value; }
        @NodeCreator static ListValue of(List<Integer> value) { return new ListValue(value) { }; }
    }
    static class MapValue { final Map<String, Long> value; @NodeCreator MapValue(Map<String, Long> value) { this.value = value; } }
}
