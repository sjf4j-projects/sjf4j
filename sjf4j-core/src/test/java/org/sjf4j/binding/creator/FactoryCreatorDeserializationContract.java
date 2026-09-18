package org.sjf4j.binding.creator;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactoryCreatorDeserializationContract {
    private final SimpleJsonBinder binding = new SimpleJsonBinder(StreamingContext.EMPTY);

    // Structural source: TestCreators#testSimpleFactory; NodeCreator/NodeProperty replace JsonCreator/JsonProperty.
    @Test void staticFactoryCreatesPropertyBasedValue() {
        FactoryValue value = (FactoryValue) binding.readNode("{\"value\":12}", FactoryValue.class);
        assertEquals("factory", value.createdBy); assertEquals(12, value.value);
    }

    // Structural source: SingleArgCreatorTest#testExplicitFactory660a; scalar input must select the public static factory over the same-typed constructor.
    @Test void staticFactoryIsUsedInsteadOfSameTypedConstructor() {
        FactoryWins value = (FactoryWins) binding.readNode("\"abc\"", FactoryWins.class);
        assertEquals("abc", value.value);
    }

    static class FactoryValue {
        final String createdBy; final int value;
        private FactoryValue(String createdBy, int value) { this.createdBy = createdBy; this.value = value; }
        @NodeCreator static FactoryValue create(@NodeProperty("value") int value) { return new FactoryValue("factory", value); }
    }
    static class FactoryWins {
        final String value;
        private FactoryWins(String value) { throw new IllegalStateException("Should not get called!"); }
        @NodeCreator public static FactoryWins create(String value) { return new FactoryWins(value, true); }
        private FactoryWins(String value, boolean factory) { this.value = value; }
    }
}
