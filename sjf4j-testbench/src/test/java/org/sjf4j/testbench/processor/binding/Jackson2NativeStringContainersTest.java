package org.sjf4j.testbench.processor.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regression coverage for Jackson2's already-advanced native String array elements. */
class Jackson2NativeStringContainersTest {

    private static final NativeStringBinder BINDER =
            CompiledInstances.create(NativeStringBinder.class);

    @Test
    void handlesRootArraysListsAndSets() throws IOException {
        byte[] json = utf8("[\"A\",null,\"雪\",\"line\\nfeed\"]");
        List<String> values = Arrays.asList("A", null, "雪", "line\nfeed");

        assertArrayEquals(values.toArray(new String[0]), BINDER.readArray(json));
        assertEquals(values, BINDER.readList(json));
        assertEquals(new LinkedHashSet<>(values), BINDER.readSet(json));

        assertArrayEquals(new String[0], BINDER.readArray(utf8("[]")));
        assertEquals(List.of(), BINDER.readList(utf8("[]")));
        assertEquals(Set.of(), BINDER.readSet(utf8("[]")));

        assertNull(BINDER.readArray(utf8("null")));
        assertNull(BINDER.readList(utf8("null")));
        assertNull(BINDER.readSet(utf8("null")));
    }

    @Test
    void handlesNestedCollectionsAndPojoFields() throws IOException {
        StringHolder holder = BINDER.readHolder(utf8(
                "{\"array\":[\"a\",null],\"list\":[\"b\",\"c\"],\"set\":[\"d\",\"d\"]}"));
        assertArrayEquals(new String[]{"a", null}, holder.array);
        assertEquals(Arrays.asList("b", "c"), holder.list);
        assertEquals(new LinkedHashSet<>(List.of("d")), holder.set);

        List<List<String>> nested = BINDER.readNested(utf8(
                "[[\"one\",null],[],null,[\"three\"]]"));
        assertEquals(Arrays.asList(
                Arrays.asList("one", null), List.of(), null, List.of("three")), nested);
    }

    @Test
    void rejectsNonStringArrayElements() {
        for (String input : List.of("[1]", "[true]", "[{}]", "[[]]")) {
            byte[] json = utf8(input);
            BindingException array = assertThrows(BindingException.class,
                    () -> BINDER.readArray(json), input);
            BindingException list = assertThrows(BindingException.class,
                    () -> BINDER.readList(json), input);
            BindingException set = assertThrows(BindingException.class,
                    () -> BINDER.readSet(json), input);
            assertInstanceOf(IOException.class, array.getCause(), input);
            assertInstanceOf(IOException.class, list.getCause(), input);
            assertInstanceOf(IOException.class, set.getCause(), input);
        }
    }

    private static byte[] utf8(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static final class StringHolder {
        public String[] array;
        public List<String> list;
        public Set<String> set;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface NativeStringBinder {
        @ReadFrom String[] readArray(byte[] json) throws IOException;
        @ReadFrom List<String> readList(byte[] json) throws IOException;
        @ReadFrom Set<String> readSet(byte[] json) throws IOException;
        @ReadFrom List<List<String>> readNested(byte[] json) throws IOException;
        @ReadFrom StringHolder readHolder(byte[] json) throws IOException;
    }
}
