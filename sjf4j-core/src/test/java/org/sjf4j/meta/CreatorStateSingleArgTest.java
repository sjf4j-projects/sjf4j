package org.sjf4j.meta;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.exception.BindingException;

import static org.junit.jupiter.api.Assertions.*;

class CreatorStateSingleArgTest {

    public static class StringValue {
        public final String value;

        @NodeCreator
        public StringValue(@NodeProperty("value") String value) {
            this.value = value;
        }
    }

    public static class PrimitiveValue {
        public final double value;

        @NodeCreator
        public PrimitiveValue(@NodeProperty("value") double value) {
            this.value = value;
        }
    }

    public static class MultiValue {
        public final String first;
        public final String second;

        @NodeCreator
        public MultiValue(@NodeProperty("first") String first,
                          @NodeProperty("second") String second) {
            this.first = first;
            this.second = second;
        }
    }

    private static CreatorInfo creator(Class<?> type) {
        return TypeRegistry.registerTypeInfo(type).pojoInfo.creatorInfo;
    }

    @Test
    void createsStringWithoutArgArrayAndRetainsAbsentValue() {
        CreatorState present = new CreatorState(creator(StringValue.class));
        present.acceptCtorArg(0, "ok");
        assertEquals("ok", ((StringValue) present.finish()).value);

        CreatorState missing = new CreatorState(creator(StringValue.class));
        assertNull(((StringValue) missing.finish()).value);
    }

    @Test
    void handlesNullAndMissingPrimitiveDefaults() {
        CreatorInfo info = creator(PrimitiveValue.class);

        CreatorState present = new CreatorState(info);
        present.acceptCtorArg(0, 12.5);
        assertEquals(12.5, ((PrimitiveValue) present.finish()).value);

        CreatorState explicitNull = new CreatorState(info);
        explicitNull.acceptCtorArg(0, null);
        assertEquals(0.0, ((PrimitiveValue) explicitNull.finish()).value);

        CreatorState missing = new CreatorState(info);
        assertEquals(0.0, ((PrimitiveValue) missing.finish()).value);
    }

    @Test
    void rejectsDuplicateArgumentAssignment() {
        CreatorState state = new CreatorState(creator(StringValue.class));
        state.acceptCtorArg(0, "first");
        assertThrows(BindingException.class, () -> state.acceptCtorArg(0, "second"));
    }

    @Test
    void multivalueCreatorsKeepExistingArraySemantics() {
        CreatorState state = new CreatorState(creator(MultiValue.class));
        state.acceptCtorArg(1, "second");
        state.acceptCtorArg(0, "first");
        MultiValue value = (MultiValue) state.finish();
        assertEquals("first", value.first);
        assertEquals("second", value.second);
    }
}
