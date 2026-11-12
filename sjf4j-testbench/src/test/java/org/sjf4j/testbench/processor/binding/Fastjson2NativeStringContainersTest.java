package org.sjf4j.testbench.processor.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regression coverage for Fastjson2 compiled native container traversal. */
class Fastjson2NativeStringContainersTest {

    private static final NativeStringBinder BINDER =
            CompiledInstances.create(NativeStringBinder.class);

    @Test
    void readsRootArraysListsAndSets() throws IOException {
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
    void readsNestedContainersAndPojoFields() throws IOException {
        StringHolder holder = BINDER.readHolder(utf8(
                "{\"array\":[\"a\",null],\"list\":[\"b\",\"c\"],"
                        + "\"set\":[\"d\",\"d\"],\"children\":[{\"id\":1},{\"id\":2}]}"));
        assertArrayEquals(new String[]{"a", null}, holder.array);
        assertEquals(Arrays.asList("b", "c"), holder.list);
        assertEquals(new LinkedHashSet<>(List.of("d")), holder.set);
        assertEquals(2, holder.children.size());
        assertEquals(1, holder.children.get(0).id);
        assertEquals(2, holder.children.get(1).id);

        List<List<String>> nested = BINDER.readNested(utf8(
                "[[\"one\",null],[],null,[\"three\"]]"));
        assertEquals(Arrays.asList(
                Arrays.asList("one", null), List.of(), null, List.of("three")), nested);
    }

    @Test
    void readsNativePrimitivesAndWrapperFallback() throws IOException {
        assertArrayEquals(new long[]{1L, 0L, 42L},
                BINDER.readLongs(utf8("[1,0,42]")));
        assertEquals(Arrays.asList(1, null, 3),
                BINDER.readIntegers(utf8("[1,null,3]")));
        assertEquals(7, BINDER.readChildren(utf8("[{\"id\":7},null]")).get(0).id);
        assertNull(BINDER.readChildren(utf8("[{\"id\":7},null]")).get(1));
    }

    @Test
    void rejectsNonArrayAndNonObjectInputs() {
        assertThrows(BindingException.class, () -> BINDER.readList(utf8("{}")));
        assertThrows(BindingException.class, () -> BINDER.readArray(utf8("123")));
        assertThrows(BindingException.class, () -> BINDER.readHolder(utf8("[]")));
    }

    private static byte[] utf8(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static final class Child {
        public int id;
    }

    public static final class StringHolder {
        public String[] array;
        public List<String> list;
        public Set<String> set;
        public List<Child> children;
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface NativeStringBinder {
        @Read String[] readArray(byte[] json) throws IOException;
        @Read List<String> readList(byte[] json) throws IOException;
        @Read Set<String> readSet(byte[] json) throws IOException;
        @Read List<List<String>> readNested(byte[] json) throws IOException;
        @Read StringHolder readHolder(byte[] json) throws IOException;
        @Read List<Child> readChildren(byte[] json) throws IOException;
        @Read long[] readLongs(byte[] json) throws IOException;
        @Read List<Integer> readIntegers(byte[] json) throws IOException;
    }
}
