package org.sjf4j.bytecode;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
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
        BytecodePath<Root, Integer> path = BytecodePath.compile("$.holder.leaf.score", Root.class, Integer.class);
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

        BytecodePath<Root, Boolean> activePath = BytecodePath.compile("$.holder.leaf.active", Root.class, Boolean.class);
        assertAsmCompiled(activePath);
        assertEquals(Boolean.TRUE, activePath.get(root));

        BytecodePath<Root, Long> idPath = BytecodePath.compile("$.holder.leaf.ids[1]", Root.class, Long.class);
        assertAsmCompiled(idPath);
        assertEquals(Long.valueOf(9L), idPath.get(root));

        BytecodePath<Root, Long> lastIdPath = BytecodePath.compile("$.holder.leaf.ids[-1]", Root.class, Long.class);
        assertAsmCompiled(lastIdPath);
        assertEquals(Long.valueOf(12L), lastIdPath.get(root));
    }

    @Test
    public void testPojoFieldPutReturnsOldValue() {
        Root root = sampleRoot();

        BytecodePath<Root, Integer> path = BytecodePath.compile("$.holder.leaf.score", Root.class, Integer.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, 11));
        assertEquals(11, root.holder.leaf.score);
    }

    @Test
    public void testPojoSetterPutReturnsOldValue() {
        Root root = sampleRoot();

        BytecodePath<Root, Boolean> path = BytecodePath.compile("$.holder.leaf.active", Root.class, Boolean.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, Boolean.FALSE));
        assertFalse(root.holder.leaf.isActive());
    }

    @Test
    public void testMapJsonObjectAndObjectArraySegments() {
        Root root = sampleRoot();

        BytecodePath<Root, Integer> mapPath = BytecodePath.compile("$.holder.buckets.good.count", Root.class, Integer.class);
        assertAsmCompiled(mapPath);
        assertEquals(Integer.valueOf(3), mapPath.get(root));

        BytecodePath<Root, String> objectArrayPath = BytecodePath.compile("$.holder.tags[1]", Root.class, String.class);
        assertAsmCompiled(objectArrayPath);
        assertEquals("y", objectArrayPath.get(root));

    }

    @Test
    public void testDynamicObjectLeafSegment() {
        Root root = sampleRoot();

        BytecodePath<Root, Object> path = BytecodePath.compile("$.holder.dynamic.n", Root.class, Object.class);
        assertAsmCompiled(path);
        assertEquals("5", path.get(root));
    }

    @Test
    public void testDynamicObjectThenArrayIndexSegment() {
        Root root = sampleRoot();
        root.holder.dynamic.put("items", List.of("zero", "one"));

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.dynamic.items[1]", Root.class, String.class);
        assertAsmCompiled(path);
        assertEquals("one", path.get(root));
    }

    @Test
    public void testMapBackedPutReturnsOldValue() {
        Root root = sampleRoot();

        BytecodePath<Root, Integer> path = BytecodePath.compile("$.holder.buckets.good.count", Root.class, Integer.class);
        assertAsmCompiled(path);

        assertEquals(Integer.valueOf(3), path.put(root, 9));
        assertEquals(9, root.holder.buckets.get("good").getInt("count"));
    }

    @Test
    public void testJsonArrayIndexSegment() {
        Root root = sampleRoot();

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.values[1]", Root.class, String.class);
        assertAsmCompiled(path);
        assertEquals("b", path.get(root));

        root.holder.values = null;
        assertNull(path.get(root));
    }

    @Test
    public void testJsonArrayIndexPutReturnsOldValue() {
        Root root = sampleRoot();

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.values[1]", Root.class, String.class);
        assertAsmCompiled(path);

        assertEquals("b", path.put(root, "bb"));
        assertEquals("bb", root.holder.values.getNode(1));
    }

    @Test
    public void testObjectArrayIndexPutReturnsOldValue() {
        Root root = sampleRoot();

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.tags[1]", Root.class, String.class);
        assertAsmCompiled(path);

        assertEquals("y", path.put(root, "yy"));
        assertEquals("yy", root.holder.tags[1]);
    }

    @Test
    public void testPojoListIndexPutReturnsOldValue() {
        Root root = sampleRoot();

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.names[1]", Root.class, String.class);
        assertAsmCompiled(path);

        assertEquals("bob", path.put(root, "beth"));
        assertEquals("beth", root.holder.names.get(1));
    }

    @Test
    public void testPojoListAppendReturnsNull() {
        Root root = sampleRoot();

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.names[+]", Root.class, String.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, "dina"));
        assertEquals("dina", root.holder.names.get(3));
    }

    @Test
    public void testAppendPutReturnsNull() {
        Root root = sampleRoot();

        BytecodePath<Root, Object> path = BytecodePath.compile("$.holder.values[+]", Root.class, Object.class);
        assertAsmCompiled(path);

        assertNull(path.put(root, "d"));
        assertEquals("d", root.holder.values.getNode(3));
    }

    @Test
    public void testIndexedPutAppendsAtSizeAndRejectsPastSize() {
        Root root = sampleRoot();

        BytecodePath<Root, Object> path = BytecodePath.compile("$.holder.values[3]", Root.class, Object.class);
        assertAsmCompiled(path);
        assertNull(path.put(root, "d"));
        assertEquals("d", root.holder.values.getNode(3));

        BytecodePath<Root, Object> pastEnd = BytecodePath.compile("$.holder.values[5]", Root.class, Object.class);
        assertAsmCompiled(pastEnd);
        JsonException ex = assertThrows(JsonException.class, () -> pastEnd.put(root, "x"));
        assertTrue(ex.getMessage().contains("cannot set at index"));
    }

    @Test
    public void testTailIndexPutListAppendAndNegativeIndexes() {
        Root root = sampleRoot();

        BytecodePath<Root, String> appendPath = BytecodePath.compile("$.holder.names[3]", Root.class, String.class);
        assertAsmCompiled(appendPath);
        assertNull(appendPath.put(root, "dina"));
        assertEquals("dina", root.holder.names.get(3));

        BytecodePath<Root, String> listPath = BytecodePath.compile("$.holder.names[-1]", Root.class, String.class);
        assertAsmCompiled(listPath);
        assertEquals("dina", listPath.put(root, "dora"));
        assertEquals("dora", root.holder.names.get(3));

        BytecodePath<Root, Object> jsonPath = BytecodePath.compile("$.holder.values[-1]", Root.class, Object.class);
        assertAsmCompiled(jsonPath);
        assertEquals("c", jsonPath.put(root, "cc"));
        assertEquals("cc", root.holder.values.getNode(2));

        BytecodePath<Root, String> arrayPath = BytecodePath.compile("$.holder.tags[-1]", Root.class, String.class);
        assertAsmCompiled(arrayPath);
        assertEquals("z", arrayPath.put(root, "zz"));
        assertEquals("zz", root.holder.tags[2]);
    }

    @Test
    public void testNegativeIndexPutBoundaries() {
        Root root = sampleRoot();

        BytecodePath<Root, String> firstList = BytecodePath.compile("$.holder.names[-3]", Root.class, String.class);
        assertAsmCompiled(firstList);
        assertEquals("ann", firstList.put(root, "amy"));
        assertEquals("amy", root.holder.names.get(0));

        BytecodePath<Root, String> listOob = BytecodePath.compile("$.holder.names[-4]", Root.class, String.class);
        assertAsmCompiled(listOob);
        JsonException listEx = assertThrows(JsonException.class, () -> listOob.put(root, "bad"));
        assertTrue(listEx.getMessage().contains("cannot set at index -4"));

        BytecodePath<Root, Object> firstJsonArray = BytecodePath.compile("$.holder.values[-3]", Root.class, Object.class);
        assertAsmCompiled(firstJsonArray);
        assertEquals("a", firstJsonArray.put(root, "aa"));
        assertEquals("aa", root.holder.values.getNode(0));

        BytecodePath<Root, Object> jsonArrayOob = BytecodePath.compile("$.holder.values[-4]", Root.class, Object.class);
        assertAsmCompiled(jsonArrayOob);
        JsonException jsonEx = assertThrows(JsonException.class, () -> jsonArrayOob.put(root, "bad"));
        assertTrue(jsonEx.getMessage().contains("cannot set at index -4"));

        BytecodePath<Root, String> firstArray = BytecodePath.compile("$.holder.tags[-3]", Root.class, String.class);
        assertAsmCompiled(firstArray);
        assertEquals("x", firstArray.put(root, "xx"));
        assertEquals("xx", root.holder.tags[0]);

        BytecodePath<Root, String> arrayOob = BytecodePath.compile("$.holder.tags[-4]", Root.class, String.class);
        assertAsmCompiled(arrayOob);
        JsonException arrayEx = assertThrows(JsonException.class, () -> arrayOob.put(root, "bad"));
        assertTrue(arrayEx.getMessage().contains("cannot set at index -4"));

        root.holder.dynamic.put("items", new ArrayList<>(List.of("zero", "one")));
        BytecodePath<Root, Object> dynamic = BytecodePath.compile("$.holder.dynamic.items[-1]", Root.class, Object.class);
        assertAsmCompiled(dynamic);
        assertEquals("one", dynamic.put(root, "uno"));
        assertEquals("uno", ((List<?>) root.holder.dynamic.getNode("items")).get(1));
    }

    @Test
    public void testStaticValueTypeMismatchFailsFast() {
        JsonException ex = assertThrows(JsonException.class,
                () -> BytecodePath.compile("$.holder.tags[1]", Root.class, Integer.class));
        assertTrue(ex.getMessage().contains("does not coerce terminal type java.lang.String"));
    }

    @Test
    public void testPrimitiveValueTypeFailsFast() {
        JsonException ex = assertThrows(JsonException.class,
                () -> BytecodePath.compile("$.holder.leaf.score", Root.class, int.class));
        assertTrue(ex.getMessage().contains("valueType must be a reference type"));
        assertTrue(ex.getMessage().contains(Integer.class.getName()));
    }

    @Test
    public void testSetIndexFailsFast() {
        JsonException ex = assertThrows(JsonException.class,
                () -> BytecodePath.compile("$.holder.keys[0]", Root.class, String.class));
        assertTrue(ex.getMessage().contains("cannot read by index from unordered Set type"));
    }

    @Test
    public void testJavaArrayAppendFailsFast() {
        JsonException ex = assertThrows(JsonException.class,
                () -> BytecodePath.compile("$.holder.tags[+]", Root.class, String.class));
        assertTrue(ex.getMessage().contains("cannot append to Java array type"));
    }

    @Test
    public void testPojoListIndexSegment() {
        BookStoreRoot root = sampleBookStoreRoot();

        BytecodePath<BookStoreRoot, Double> path = BytecodePath.compile("$.store.book[1].price", BookStoreRoot.class, Double.class);
        assertAsmCompiled(path);
        assertEquals(Double.valueOf(12.99d), path.get(root));

        root.store.book = null;
        assertNull(path.get(root));
    }

    @Test
    public void testAppendPathThrowsOnGet() {
        BytecodePath<Root, Object> path = BytecodePath.compile("$.holder.values[+]", Root.class, Object.class);
        assertAsmCompiled(path);

        JsonException ex = assertThrows(JsonException.class, () -> path.get(sampleRoot()));
        assertTrue(ex.getMessage().contains("append"));
    }

    @Test
    public void testPointerIndexArrayCompilesAndObjectFailsFast() {
        BytecodePath<JsonArray, Object> arrayPath = BytecodePath.compile("/0", JsonArray.class, Object.class);
        assertAsmCompiled(arrayPath);
        assertEquals("a", arrayPath.get(JsonArray.of("a", "b")));

        JsonException ex = assertThrows(JsonException.class,
                () -> BytecodePath.compile("/0", JsonObject.class, Object.class));
        assertTrue(ex.getMessage().contains("array-like target"));
    }

    @Test
    public void testPutNullRootThrows() {
        BytecodePath<Root, Integer> path = BytecodePath.compile("$.holder.leaf.score", Root.class, Integer.class);
        assertAsmCompiled(path);

        assertThrows(NullPointerException.class, () -> path.put(null, 1));
    }

    @Test
    public void testPutMissingParentThrows() {
        Root root = sampleRoot();
        root.holder.leaf = null;

        BytecodePath<Root, Integer> path = BytecodePath.compile("$.holder.leaf.score", Root.class, Integer.class);
        assertAsmCompiled(path);

        JsonException ex = assertThrows(JsonException.class, () -> path.put(root, 1));
        assertTrue(ex.getMessage().contains("parent container does not exist"));
    }

    @Test
    public void testEnsurePutCreatesPojoNullChain() {
        Root root = new Root();

        BytecodePath<Root, Integer> path = BytecodePath.compile("$.holder.leaf.score", Root.class, Integer.class);
        assertAsmCompiled(path);

        assertNull(path.ensurePut(root, 41));
        assertEquals(Integer.valueOf(41), path.get(root));
        assertEquals(41, root.holder.leaf.score);
    }

    @Test
    public void testEnsurePutCreatesMapAndJsonContainers() {
        Root root = sampleRoot();
        root.holder.buckets = new LinkedHashMap<>();

        BytecodePath<Root, Integer> mapPath = BytecodePath.compile("$.holder.buckets.good.count", Root.class, Integer.class);
        assertAsmCompiled(mapPath);
        assertNull(mapPath.ensurePut(root, 3));
        assertEquals(Integer.valueOf(3), mapPath.get(root));
        assertInstanceOf(JsonObject.class, root.holder.buckets.get("good"));

        root.holder.dynamic = new JsonObject();
        BytecodePath<Root, String> jsonPath = BytecodePath.compile("$.holder.dynamic.items[+].name", Root.class, String.class);
        assertAsmCompiled(jsonPath);
        assertNull(jsonPath.ensurePut(root, "Alice"));
        assertInstanceOf(ArrayList.class, root.holder.dynamic.getNode("items"));
        assertEquals("Alice", ((Map<?, ?>) ((List<?>) root.holder.dynamic.getNode("items")).get(0)).get("name"));
    }

    @Test
    public void testEnsurePutCreatesCustomMapAndListContainers() {
        Root root = sampleRoot();
        root.holder.customBuckets = null;
        root.holder.customBooks = null;
        root.holder.keys = null;

        BytecodePath<Root, Integer> mapPath = BytecodePath.compile("$.holder.customBuckets.good.count", Root.class, Integer.class);
        assertAsmCompiled(mapPath);
        assertNull(mapPath.ensurePut(root, 8));
        assertInstanceOf(CustomBuckets.class, root.holder.customBuckets);
        assertEquals(8, root.holder.customBuckets.get("good").getInt("count"));

        BytecodePath<Root, Double> listPath = BytecodePath.compile("$.holder.customBooks[+].price", Root.class, Double.class);
        assertAsmCompiled(listPath);
        assertNull(listPath.ensurePut(root, 19.5d));
        assertInstanceOf(CustomBookList.class, root.holder.customBooks);
        assertEquals(Double.valueOf(19.5d), root.holder.customBooks.get(0).price);

        BytecodePath<Root, String> setPath = BytecodePath.compile("$.holder.keys[+]", Root.class, String.class);
        assertAsmCompiled(setPath);
        assertNull(setPath.ensurePut(root, "k3"));
        assertInstanceOf(LinkedHashSet.class, root.holder.keys);
        assertTrue(root.holder.keys.contains("k3"));
    }

    @Test
    public void testEnsurePutCreatesAppendIntermediateElement() {
        Root root = sampleRoot();
        root.holder.values = new JsonArray();

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.values[+].name", Root.class, String.class);
        assertAsmCompiled(path);

        assertNull(path.ensurePut(root, "neo"));
        assertEquals("neo", ((Map<?, ?>) root.holder.values.getNode(0)).get("name"));
    }

    @Test
    public void testEnsurePutMiddleListIndexNullCreatesAndSupportsNegativeIndex() {
        BookStoreRoot root = new BookStoreRoot();
        root.store = new BookStore();
        root.store.book = new ArrayList<>();
        root.store.book.add(null);
        root.store.book.add(null);

        BytecodePath<BookStoreRoot, Double> first = BytecodePath.compile("$.store.book[0].price", BookStoreRoot.class, Double.class);
        assertAsmCompiled(first);
        assertNull(first.ensurePut(root, 10.5d));
        assertEquals(Double.valueOf(10.5d), first.get(root));

        BytecodePath<BookStoreRoot, Double> last = BytecodePath.compile("$.store.book[-1].price", BookStoreRoot.class, Double.class);
        assertAsmCompiled(last);
        assertNull(last.ensurePut(root, 12.5d));
        assertEquals(Double.valueOf(12.5d), last.get(root));

        BytecodePath<BookStoreRoot, Double> firstByNegative =
                BytecodePath.compile("$.store.book[-2].price", BookStoreRoot.class, Double.class);
        assertAsmCompiled(firstByNegative);
        assertEquals(Double.valueOf(10.5d), firstByNegative.get(root));

        BytecodePath<BookStoreRoot, Double> oob =
                BytecodePath.compile("$.store.book[-3].price", BookStoreRoot.class, Double.class);
        assertAsmCompiled(oob);
        JsonException ex = assertThrows(JsonException.class, () -> oob.ensurePut(root, 1.5d));
        assertTrue(ex.getMessage().contains("indexed array access requires an existing element"));
    }

    @Test
    public void testEnsurePutMiddleJsonArrayIndexNullCreatesAndOutOfRangeThrows() {
        Root root = sampleRoot();
        root.holder.values = JsonArray.of((Object) null);

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.values[0].name", Root.class, String.class);
        assertAsmCompiled(path);
        assertNull(path.ensurePut(root, "zero"));
        assertEquals("zero", ((Map<?, ?>) root.holder.values.getNode(0)).get("name"));

        root.holder.values = new JsonArray();
        assertNull(path.ensurePut(root, "appended"));
        assertEquals("appended", ((Map<?, ?>) root.holder.values.getNode(0)).get("name"));

        BytecodePath<Root, String> outOfRange = BytecodePath.compile("$.holder.values[2].name", Root.class, String.class);
        assertAsmCompiled(outOfRange);
        JsonException ex = assertThrows(JsonException.class, () -> outOfRange.ensurePut(root, "bad"));
        assertTrue(ex.getMessage().contains("indexed array access requires an existing element"));
    }

    @Test
    public void testEnsurePutMiddleJsonArrayNegativeIndexBoundaries() {
        Root root = sampleRoot();
        root.holder.values = JsonArray.of(null, null);

        BytecodePath<Root, String> first = BytecodePath.compile("$.holder.values[-2].name", Root.class, String.class);
        assertAsmCompiled(first);
        assertNull(first.ensurePut(root, "first"));
        assertEquals("first", ((Map<?, ?>) root.holder.values.getNode(0)).get("name"));

        BytecodePath<Root, String> oob = BytecodePath.compile("$.holder.values[-3].name", Root.class, String.class);
        assertAsmCompiled(oob);
        JsonException ex = assertThrows(JsonException.class, () -> oob.ensurePut(root, "bad"));
        assertTrue(ex.getMessage().contains("indexed array access requires an existing element"));
    }

    @Test
    public void testEnsurePutIndexedIntermediateAppendsAtSizeUsingPropertyTypes() {
        CompiledPropertyRoot root = new CompiledPropertyRoot();

        BytecodePath<CompiledPropertyRoot, String> first =
                BytecodePath.compile("$.a.b[0].c", CompiledPropertyRoot.class, String.class);
        BytecodePath<CompiledPropertyRoot, String> second =
                BytecodePath.compile("$.a.b[1].c", CompiledPropertyRoot.class, String.class);
        assertAsmCompiled(first);
        assertAsmCompiled(second);

        assertNull(first.ensurePut(root, "b0"));
        assertNull(second.ensurePut(root, "b1"));

        assertEquals(2, root.a.b.size());
        assertEquals("b0", root.a.b.get(0).c);
        assertEquals("b1", root.a.b.get(1).c);
    }

    @Test
    public void testEnsurePutUnsupportedJavaArrayCreationThrowsAtRuntime() {
        Root root = sampleRoot();
        root.holder.tags = null;

        BytecodePath<Root, String> path = BytecodePath.compile("$.holder.tags[0]", Root.class, String.class);
        assertAsmCompiled(path);

        JsonException ex = assertThrows(JsonException.class, () -> path.ensurePut(root, "x"));
        assertTrue(ex.getMessage().contains("cannot create array container"));
    }

    @Test
    public void testCompiledPathSupportsNegativeJavaArrayIndex() {
        Root root = sampleRoot();
        Book book = new Book();
        book.price = 7.5d;
        root.holder.books = new Book[]{book};

        BytecodePath<Root, Double> path = BytecodePath.compile("$.holder.books[-1].price", Root.class, Double.class);
        assertAsmCompiled(path);
        assertEquals(Double.valueOf(7.5d), path.get(root));
        assertNull(path.ensurePut(root, 8.5d));
        assertEquals(Double.valueOf(8.5d), book.price);

        BytecodePath<Root, Double> oob = BytecodePath.compile("$.holder.books[-2].price", Root.class, Double.class);
        assertAsmCompiled(oob);
        JsonException ex = assertThrows(JsonException.class, () -> oob.ensurePut(root, 9.5d));
        assertTrue(ex.getMessage().contains("indexed array access requires an existing element"));
    }

    @Test
    public void testComputeNegativeIndex() {
        Root root = sampleRoot();

        BytecodePath<Root, String> listPath = BytecodePath.compile("$.holder.names[-1]", Root.class, String.class);
        assertAsmCompiled(listPath);
        assertEquals(1, listPath.compute(root, (parent, current) -> current + "!"));
        assertEquals("cara!", root.holder.names.get(2));

        BytecodePath<Root, Object> jsonArrayPath = BytecodePath.compile("$.holder.values[-1]", Root.class, Object.class);
        assertAsmCompiled(jsonArrayPath);
        assertEquals(1, jsonArrayPath.compute(root, (parent, current) -> current + "!"));
        assertEquals("c!", root.holder.values.getNode(2));

        BytecodePath<Root, String> arrayPath = BytecodePath.compile("$.holder.tags[-1]", Root.class, String.class);
        assertAsmCompiled(arrayPath);
        assertEquals(1, arrayPath.compute(root, (parent, current) -> current + "!"));
        assertEquals("z!", root.holder.tags[2]);

        BytecodePath<Root, String> oob = BytecodePath.compile("$.holder.names[-4]", Root.class, String.class);
        assertAsmCompiled(oob);
        JsonException ex = assertThrows(JsonException.class, () -> oob.compute(root, (parent, current) -> "bad"));
        assertTrue(ex.getMessage().contains("cannot set at index -4"));
    }

    @Test
    public void testSetterOnlyMiddlePojoPropertyFailsAtCompileTime() {
        JsonException ex = assertThrows(JsonException.class,
                () -> BytecodePath.compile("$.holder.leaf.score", SetterOnlyRoot.class, Integer.class));
        assertTrue(ex.getMessage().contains("readable property"));
    }

    private static void assertAsmCompiled(BytecodePath<?, ?> path) {
        assertFalse(path instanceof FallbackBytecodePath);
        assertTrue(path.getClass().getName().startsWith("org.sjf4j.bytecode.generated.BytecodePath_"));
        assertInstanceOf(BytecodePath.class, path);
    }

    private static Root sampleRoot() {
        PrimitiveLeaf leaf = new PrimitiveLeaf();
        leaf.score = 7;
        leaf.ids = new long[]{4L, 9L, 12L};
        leaf.active = true;

        Holder holder = new Holder();
        holder.leaf = leaf;
        holder.tags = new String[]{"x", "y", "z"};
        holder.books = new Book[0];
        holder.values = JsonArray.of("a", "b", "c");
        holder.names = new ArrayList<>(List.of("ann", "bob", "cara"));
        holder.dynamic = JsonObject.of("n", "5");
        holder.keys = new LinkedHashSet<>();
        holder.keys.add("k1");
        holder.keys.add("k2");
        holder.buckets = new LinkedHashMap<>();
        holder.buckets.put("good", JsonObject.of("count", 3));
        holder.customBuckets = new CustomBuckets();
        holder.customBooks = new CustomBookList();

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

    public static class SetterOnlyRoot {
        private Holder holder;

        public void setHolder(Holder holder) {
            this.holder = holder;
        }
    }

    public static class Holder {
        public PrimitiveLeaf leaf;
        public Map<String, JsonObject> buckets;
        public CustomBuckets customBuckets;
        public JsonObject dynamic;
        public java.util.Set<String> keys;
        public String[] tags;
        public Book[] books;
        public CustomBookList customBooks;
        public JsonArray values;
        public List<String> names;
    }

    public static class CustomBuckets extends LinkedHashMap<String, JsonObject> {
    }

    public static class CustomBookList extends ArrayList<Book> {
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

    public static class CompiledPropertyRoot {
        public CompiledPropertyA a;
    }

    public static class CompiledPropertyA {
        public List<CompiledPropertyB> b;
    }

    public static class CompiledPropertyB {
        public String c;
    }
}
