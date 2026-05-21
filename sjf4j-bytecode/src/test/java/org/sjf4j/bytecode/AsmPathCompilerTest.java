package org.sjf4j.bytecode;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.compiled.CompiledPath;
import org.sjf4j.compiled.FallbackCompiledPath;
import org.sjf4j.exception.JsonException;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsmPathCompilerTest {

    @Test
    public void testPojoFieldAndNullChain() {
        CompiledPath<Root, Integer> path = CompiledPath.compile("$.holder.leaf.score", Root.class, Integer.class);
        assertAsmCompiled(path);
        assertEquals("$.holder.leaf.score", path.expr());

        Root root = sampleRoot();
        assertEquals(Integer.valueOf(7), path.get(root));
        assertNull(path.get(null));

        root.holder.leaf = null;
        assertNull(path.get(root));
    }

    @Test
    public void testPrimitiveGetterAndPrimitiveArrayElement() {
        Root root = sampleRoot();

        CompiledPath<Root, Boolean> activePath = CompiledPath.compile("$.holder.leaf.active", Root.class, Boolean.class);
        assertAsmCompiled(activePath);
        assertEquals(Boolean.TRUE, activePath.get(root));

        CompiledPath<Root, Long> idPath = CompiledPath.compile("$.holder.leaf.ids[1]", Root.class, Long.class);
        assertAsmCompiled(idPath);
        assertEquals(Long.valueOf(9L), idPath.get(root));
    }

    @Test
    public void testPojoFieldPutReturnsOldValue() {
        Root root = sampleRoot();

        CompiledPath<Root, Integer> path = CompiledPath.compile("$.holder.leaf.score", Root.class, Integer.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, 11));
        assertEquals(11, root.holder.leaf.score);
    }

    @Test
    public void testPojoSetterPutReturnsOldValue() {
        Root root = sampleRoot();

        CompiledPath<Root, Boolean> path = CompiledPath.compile("$.holder.leaf.active", Root.class, Boolean.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, Boolean.FALSE));
        assertFalse(root.holder.leaf.isActive());
    }

    @Test
    public void testMapJsonObjectAndObjectArraySegments() {
        Root root = sampleRoot();

        CompiledPath<Root, Integer> mapPath = CompiledPath.compile("$.holder.buckets.good.count", Root.class, Integer.class);
        assertAsmCompiled(mapPath);
        assertEquals(Integer.valueOf(3), mapPath.get(root));

        CompiledPath<Root, String> objectArrayPath = CompiledPath.compile("$.holder.tags[1]", Root.class, String.class);
        assertAsmCompiled(objectArrayPath);
        assertEquals("y", objectArrayPath.get(root));

    }

    @Test
    public void testDynamicObjectLeafSegment() {
        Root root = sampleRoot();

        CompiledPath<Root, Object> path = CompiledPath.compile("$.holder.dynamic.n", Root.class, Object.class);
        assertAsmCompiled(path);
        assertEquals("5", path.get(root));
    }

    @Test
    public void testMapBackedPutReturnsOldValue() {
        Root root = sampleRoot();

        CompiledPath<Root, Integer> path = CompiledPath.compile("$.holder.buckets.good.count", Root.class, Integer.class);
        assertAsmCompiled(path);

        assertEquals(Integer.valueOf(3), path.put(root, 9));
        assertEquals(9, root.holder.buckets.get("good").getInt("count"));
    }

    @Test
    public void testJsonArrayIndexSegment() {
        Root root = sampleRoot();

        CompiledPath<Root, String> path = CompiledPath.compile("$.holder.values[1]", Root.class, String.class);
        assertAsmCompiled(path);
        assertEquals("b", path.get(root));

        root.holder.values = null;
        assertNull(path.get(root));
    }

    @Test
    public void testJsonArrayIndexPutReturnsNull() {
        Root root = sampleRoot();

        CompiledPath<Root, String> path = CompiledPath.compile("$.holder.values[1]", Root.class, String.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, "bb"));
        assertEquals("bb", root.holder.values.getNode(1));
    }

    @Test
    public void testObjectArrayIndexPutReturnsNull() {
        Root root = sampleRoot();

        CompiledPath<Root, String> path = CompiledPath.compile("$.holder.tags[1]", Root.class, String.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, "yy"));
        assertEquals("yy", root.holder.tags[1]);
    }

    @Test
    public void testPojoListIndexPutReturnsNull() {
        Root root = sampleRoot();

        CompiledPath<Root, String> path = CompiledPath.compile("$.holder.names[1]", Root.class, String.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, "beth"));
        assertEquals("beth", root.holder.names.get(1));
    }

    @Test
    public void testPojoListAppendReturnsNull() {
        Root root = sampleRoot();

        CompiledPath<Root, String> path = CompiledPath.compile("$.holder.names[+]", Root.class, String.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, "dina"));
        assertEquals("dina", root.holder.names.get(3));
    }

    @Test
    public void testAppendPutReturnsNull() {
        Root root = sampleRoot();

        CompiledPath<Root, Object> path = CompiledPath.compile("$.holder.values[+]", Root.class, Object.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, "d"));
        assertEquals("d", root.holder.values.getNode(3));
    }

    @Test
    public void testIndexedPutDoesNotAppend() {
        Root root = sampleRoot();

        CompiledPath<Root, Object> path = CompiledPath.compile("$.holder.values[3]", Root.class, Object.class);
        assertAsmCompiled(path);

        JsonException ex = assertThrows(JsonException.class, () -> path.put(root, "d"));
        assertTrue(ex.getMessage().contains("cannot set at index 3"));
    }

    @Test
    public void testStaticValueTypeMismatchFailsFast() {
        JsonException ex = assertThrows(JsonException.class,
                () -> CompiledPath.compile("$.holder.tags[1]", Root.class, Integer.class));
        assertTrue(ex.getMessage().contains("does not coerce terminal type java.lang.String"));
    }

    @Test
    public void testSetIndexFailsFast() {
        JsonException ex = assertThrows(JsonException.class,
                () -> CompiledPath.compile("$.holder.keys[0]", Root.class, String.class));
        assertTrue(ex.getMessage().contains("cannot read by index from unordered Set type"));
    }

    @Test
    public void testPojoListIndexSegment() {
        BookStoreRoot root = sampleBookStoreRoot();

        CompiledPath<BookStoreRoot, Double> path = CompiledPath.compile("$.store.book[1].price", BookStoreRoot.class, Double.class);
        assertAsmCompiled(path);
        assertEquals(Double.valueOf(12.99d), path.get(root));

        root.store.book = null;
        assertNull(path.get(root));
    }

    @Test
    public void testAppendPathThrowsOnGet() {
        CompiledPath<Root, Object> path = CompiledPath.compile("$.holder.values[+]", Root.class, Object.class);
        assertAsmCompiled(path);

        JsonException ex = assertThrows(JsonException.class, () -> path.get(sampleRoot()));
        assertTrue(ex.getMessage().contains("append"));
    }

    @Test
    public void testPointerIndexArrayCompilesAndObjectFailsFast() {
        CompiledPath<JsonArray, Object> arrayPath = CompiledPath.compile("/0", JsonArray.class, Object.class);
        assertAsmCompiled(arrayPath);
        assertEquals("a", arrayPath.get(JsonArray.of("a", "b")));

        JsonException ex = assertThrows(JsonException.class,
                () -> CompiledPath.compile("/0", JsonObject.class, Object.class));
        assertTrue(ex.getMessage().contains("array-like target"));
    }

    @Test
    public void testPutNullRootThrows() {
        CompiledPath<Root, Integer> path = CompiledPath.compile("$.holder.leaf.score", Root.class, Integer.class);
        assertAsmCompiled(path);

        assertThrows(NullPointerException.class, () -> path.put(null, 1));
    }

    @Test
    public void testPutMissingParentThrows() {
        Root root = sampleRoot();
        root.holder.leaf = null;

        CompiledPath<Root, Integer> path = CompiledPath.compile("$.holder.leaf.score", Root.class, Integer.class);
        assertAsmCompiled(path);

        JsonException ex = assertThrows(JsonException.class, () -> path.put(root, 1));
        assertTrue(ex.getMessage().contains("parent container does not exist"));
    }

    private static void assertAsmCompiled(CompiledPath<?, ?> path) {
        assertFalse(path instanceof FallbackCompiledPath);
        assertTrue(path.getClass().getName().startsWith("org.sjf4j.bytecode.generated.CompiledPath_"));
        assertInstanceOf(CompiledPath.class, path);
    }

    private static Root sampleRoot() {
        PrimitiveLeaf leaf = new PrimitiveLeaf();
        leaf.score = 7;
        leaf.ids = new long[]{4L, 9L, 12L};
        leaf.active = true;

        Holder holder = new Holder();
        holder.leaf = leaf;
        holder.tags = new String[]{"x", "y", "z"};
        holder.values = JsonArray.of("a", "b", "c");
        holder.names = new ArrayList<>(List.of("ann", "bob", "cara"));
        holder.dynamic = JsonObject.of("n", "5");
        holder.keys = new LinkedHashSet<>();
        holder.keys.add("k1");
        holder.keys.add("k2");
        holder.buckets = new LinkedHashMap<>();
        holder.buckets.put("good", JsonObject.of("count", 3));

        Root root = new Root();
        root.holder = holder;
        return root;
    }

    private static BookStoreRoot sampleBookStoreRoot() {
        Book first = new Book();
        first.price = 8.95d;
        Book second = new Book();
        second.price = 12.99d;

        BookStore store = new BookStore();
        store.book = List.of(first, second);

        BookStoreRoot root = new BookStoreRoot();
        root.store = store;
        return root;
    }

    public static class Root {
        public Holder holder;
    }

    public static class Holder {
        public PrimitiveLeaf leaf;
        public Map<String, JsonObject> buckets;
        public JsonObject dynamic;
        public java.util.Set<String> keys;
        public String[] tags;
        public JsonArray values;
        public List<String> names;
    }

    public static class PrimitiveLeaf {
        public int score;
        public long[] ids;
        private boolean active;

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static class BookStoreRoot {
        public BookStore store;
    }

    public static class BookStore {
        public List<Book> book;
    }

    public static class Book {
        public Double price;
    }
}
