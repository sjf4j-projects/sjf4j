package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.node.NodeValueCodec;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamingIOReadTest {
    @Test
    void readsUntypedNestedObjectAndArray() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"items\":[1,true]}"))) {
            Map<?, ?> value = (Map<?, ?>) StreamingIO.readNode(reader, Object.class, StreamingContext.EMPTY);
            assertEquals(List.of(1, true), value.get("items"));
        }
    }

    @Test
    void readsPrimitiveArrayIntoExactLength() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[3,4]"))) {
            int[] value = (int[]) StreamingIO.readNode(reader, int[].class, StreamingContext.EMPTY);
            assertEquals(2, value.length);
            assertEquals(4, value[1]);
        }
    }

    @Test
    void readsEnumOrdinalsAndCharset() throws Exception {
        try (SimpleJsonReader number = new SimpleJsonReader(new StringReader("1"));
             SimpleJsonReader charset = new SimpleJsonReader(new StringReader("\"UTF-8\""))) {
            assertEquals(SampleEnum.SECOND, StreamingIO.readNode(number, SampleEnum.class, StreamingContext.EMPTY));
            assertEquals(Charset.forName("UTF-8"), StreamingIO.readNode(charset, Charset.class, StreamingContext.EMPTY));
        }
    }

    @Test
    void rejectsInvalidEnumOrdinalsAndAbstractObjectTargets() throws Exception {
        try (SimpleJsonReader negative = new SimpleJsonReader(new StringReader("-1"));
             SimpleJsonReader outOfRange = new SimpleJsonReader(new StringReader("2"));
             SimpleJsonReader quoted = new SimpleJsonReader(new StringReader("\"1\""));
             SimpleJsonReader abstractRoot = new SimpleJsonReader(new StringReader("{}"));
             SimpleJsonReader abstractField = new SimpleJsonReader(new StringReader("{\"value\":{}}"))) {
            assertThrows(BindingException.class, () -> StreamingIO.readNode(negative, SampleEnum.class, StreamingContext.EMPTY));
            assertThrows(BindingException.class, () -> StreamingIO.readNode(outOfRange, SampleEnum.class, StreamingContext.EMPTY));
            assertThrows(BindingException.class, () -> StreamingIO.readNode(quoted, SampleEnum.class, StreamingContext.EMPTY));
            assertThrows(BindingException.class, () -> StreamingIO.readNode(abstractRoot, AbstractValue.class, StreamingContext.EMPTY));
            assertThrows(BindingException.class, () -> StreamingIO.readNode(abstractField, AbstractHolder.class, StreamingContext.EMPTY));
        }
    }

    @Test
    void parentCodecDoesNotClassifyConcreteReadTarget() throws Exception {
        TypeRegistry.registerValueCodec(new NodeValueCodec.SimpleValueCodec<>(CodecParent.class, String.class,
                value -> value.value, CodecParent::new));
        assertNull(TypeRegistry.registerTypeInfo(CodecChild.class).nodeValueInfo);

        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"parent\""))) {
            assertThrows(BindingException.class,
                    () -> StreamingIO.readNode(reader, CodecChild.class, StreamingContext.EMPTY));
        }
    }

    enum SampleEnum { FIRST, SECOND }

    abstract static class AbstractValue { }

    static class AbstractHolder {
        public AbstractValue value;
    }

    static class CodecParent {
        final String value;

        CodecParent(String value) {
            this.value = value;
        }
    }

    static class CodecChild extends CodecParent {
        CodecChild() {
            super("child");
        }
    }
}
