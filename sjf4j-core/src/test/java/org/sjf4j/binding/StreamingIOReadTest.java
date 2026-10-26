package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.simple.SimpleJsonParser;
import org.sjf4j.binding.simple.SimpleJsonGenerator;
import org.sjf4j.exception.BindingException;
import org.sjf4j.value.ValueRegistry;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.value.ValueCodec;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StreamingIOReadTest {
    @Test
    void readsUntypedNestedObjectAndArray() throws Exception {
        {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"items\":[1,true]}"));
            try {
                Map<?, ?> value = (Map<?, ?>) StreamingIO.readNode(reader, Object.class, RuntimeContext.EMPTY);
                assertEquals(List.of(1, true), value.get("items"));
            } finally {
                reader.release();
            }
        }
    }

    @Test
    void readsNestedGenericMapAndListUsingInterfaceFastPaths() throws Exception {
        TypeReference<Map<String, List<Integer>>> target =
                new TypeReference<Map<String, List<Integer>>>() {};
        {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"first\":[1,2],\"second\":[]}"));
            try {
                Map<?, ?> value = assertInstanceOf(Map.class,
                        StreamingIO.readNode(reader, target.getType(), RuntimeContext.EMPTY));
                assertEquals(List.of(1, 2), assertInstanceOf(List.class, value.get("first")));
                assertEquals(List.of(), assertInstanceOf(List.class, value.get("second")));
            } finally {
                reader.release();
            }
        }
    }

    @Test
    void readsPrimitiveArrayIntoExactLength() throws Exception {
        {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("[3,4]"));
            try {
                int[] value = (int[]) StreamingIO.readNode(reader, int[].class, RuntimeContext.EMPTY);
                assertEquals(2, value.length);
                assertEquals(4, value[1]);
            } finally {
                reader.release();
            }
        }
    }

    @Test
    void readsEnumOrdinalsAndCharset() throws Exception {
        {
            SimpleJsonParser number = new SimpleJsonParser(new StringReader("1"));
            SimpleJsonParser charset = new SimpleJsonParser(new StringReader("\"UTF-8\""));
            try {
                assertEquals(SampleEnum.SECOND, StreamingIO.readNode(number, SampleEnum.class, RuntimeContext.EMPTY));
                assertEquals(Charset.forName("UTF-8"), StreamingIO.readNode(charset, Charset.class, RuntimeContext.EMPTY));
            } finally {
                charset.release();
                number.release();
            }
        }
    }

    @Test
    void readsCharsetAfterWritingRuntimeCharsetSubtype() throws Exception {
        {
            SimpleJsonGenerator writer = new SimpleJsonGenerator(null, new StringWriter());
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("\"UTF-8\""));
            try {
                StreamingIO.writeNode(writer, StandardCharsets.UTF_8, RuntimeContext.EMPTY);
                assertEquals(StandardCharsets.UTF_8, StreamingIO.readNode(reader, Charset.class, RuntimeContext.EMPTY));
            } finally {
                reader.release();
                writer.release();
            }
        }
    }

    @Test
    void rejectsInvalidEnumOrdinalsAndAbstractObjectTargets() throws Exception {
        {
            SimpleJsonParser negative = new SimpleJsonParser(new StringReader("-1"));
            SimpleJsonParser outOfRange = new SimpleJsonParser(new StringReader("2"));
            SimpleJsonParser quoted = new SimpleJsonParser(new StringReader("\"1\""));
            SimpleJsonParser abstractRoot = new SimpleJsonParser(new StringReader("{}"));
            SimpleJsonParser abstractField = new SimpleJsonParser(new StringReader("{\"value\":{}}"));
            try {
                assertThrows(BindingException.class, () -> StreamingIO.readNode(negative, SampleEnum.class, RuntimeContext.EMPTY));
                assertThrows(BindingException.class, () -> StreamingIO.readNode(outOfRange, SampleEnum.class, RuntimeContext.EMPTY));
                assertThrows(BindingException.class, () -> StreamingIO.readNode(quoted, SampleEnum.class, RuntimeContext.EMPTY));
                assertThrows(BindingException.class, () -> StreamingIO.readNode(abstractRoot, AbstractValue.class, RuntimeContext.EMPTY));
                assertThrows(BindingException.class, () -> StreamingIO.readNode(abstractField, AbstractHolder.class, RuntimeContext.EMPTY));
            } finally {
                abstractField.release();
                abstractRoot.release();
                quoted.release();
                outOfRange.release();
                negative.release();
            }
        }
    }

    @Test
    void parentCodecDoesNotClassifyConcreteReadTarget() throws Exception {
        ValueRegistry.registerByCodec(new ValueCodec.SimpleValueCodec<>(CodecParent.class, String.class,
                value -> value.value, CodecParent::new),  null, false);
        assertNotNull(TypeRegistry.registerTypeInfo(CodecChild.class).valueSet);

        {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("\"parent\""));
            try {
                assertThrows(BindingException.class, () -> StreamingIO.readNode(reader, CodecChild.class, RuntimeContext.EMPTY));
            } finally {
                reader.release();
            }
        }
    }

    @Test
    void distinguishesUnresolvedAbsentAndResolvedReadMetadata() throws Exception {
        // null: unresolved, resolves the POJO only after the built-in paths.
        {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"name\":\"Ada\"}"));
            try {
                NameHolder value = (NameHolder) StreamingIO.readNode(
                        reader, NameHolder.class, NameHolder.class, null, RuntimeContext.EMPTY);
                assertEquals("Ada", value.name);
            } finally {
                reader.release();
            }
        }

        // NONE: already resolved; standard Java types need no registry lookup.
        {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"a\":1}"));
            try {
                Map<?, ?> result = (Map<?, ?>) StreamingIO.readNode(
                        reader, Map.class, Map.class, TypeInfo.NONE, RuntimeContext.EMPTY);
                assertEquals(1, result.get("a"));
            } finally {
                reader.release();
            }
        }

        // Concrete metadata: dispatches directly to POJO reading.
        TypeInfo info = TypeRegistry.registerTypeInfo(NameHolder.class);
        {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"name\":\"Bob\"}"));
            try {
                NameHolder value = (NameHolder) StreamingIO.readNode(
                        reader, NameHolder.class, NameHolder.class, info, RuntimeContext.EMPTY);
                assertEquals("Bob", value.name);
            } finally {
                reader.release();
            }
        }

        // NONE for a non-standard type must not silently resolve metadata.
        {
            SimpleJsonParser reader = new SimpleJsonParser(new StringReader("{\"name\":\"Eve\"}"));
            try {
                assertThrows(BindingException.class, () -> StreamingIO.readNode(
                        reader, NameHolder.class, NameHolder.class, TypeInfo.NONE, RuntimeContext.EMPTY));
            } finally {
                reader.release();
            }
        }
    }

    static class NameHolder {
        public String name;
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
