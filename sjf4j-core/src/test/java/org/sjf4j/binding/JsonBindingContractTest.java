package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Portable streaming-binding behavior. Subclasses opt parser bindings into this contract. */
public abstract class JsonBindingContractTest {

    protected abstract JsonBinder<?, ?> binding(StreamingContext context);

    @Test
    void readerAndWriterExposeStructuralJson() throws Exception {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);
        StringWriter output = new StringWriter();
        try (StreamingWriter writer = binding.createWriter(output)) {
            writer.startDocument();
            writer.startObject();
            writer.writeName("items");
            writer.startArray();
            writer.writeIntValue(1);
            writer.separateElement();
            writer.writeStringValue("x");
            writer.endArray();
            writer.separateProperty();
            writer.writeName("ok");
            writer.writeBoolean(true);
            writer.endObject();
            writer.endDocument();
            writer.flush();
        }
        try (StreamingReader reader = binding.createReader(new StringReader(output.toString()))) {
            reader.startDocument();
            reader.startObject();
            assertEquals("items", reader.nextName());
            reader.startArray();
            assertEquals(1, reader.nextIntValue());
            assertEquals("x", reader.nextString());
            reader.endArray();
            assertEquals("ok", reader.nextName());
            assertTrue(reader.nextBooleanValue());
            reader.endObject();
            reader.endDocument();
        }
    }

    @Test
    void bindsScalarsAndContainers() {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);
        assertNull(binding.readNode("null", String.class));
        assertEquals(Optional.empty(), binding.readNode("null", new TypeReference<Optional<String>>() {}.getType()));
        assertEquals(1, binding.readNode("1", Integer.class));
        assertEquals(new BigInteger("7"), binding.readNode("7", BigInteger.class));
        assertEquals(new BigDecimal("8.25"), binding.readNode("8.25", BigDecimal.class));
        assertEquals('x', binding.readNode("\"x\"", Character.class));
        assertEquals(Kind.FIRST, binding.readNode("\"FIRST\"", Kind.class));

        Map<String, Integer> map = cast(binding.readNode("{\"one\":1,\"two\":2}",
                new TypeReference<Map<String, Integer>>() {}.getType()));
        assertEquals(Map.of("one", 1, "two", 2), map);
        assertEquals(Arrays.asList(1, 2), binding.readNode("[1,2]", new TypeReference<List<Integer>>() {}.getType()));
        assertEquals(Set.of(1, 2), binding.readNode("[1,2,1]", new TypeReference<Set<Integer>>() {}.getType()));
        assertArrayEquals(new int[]{1, 2}, (int[]) binding.readNode("[1,2]", int[].class));
        Object raw = binding.readNode("{\"items\":[1,true]}", Object.class);
        assertInstanceOf(Map.class, raw);
        List<?> rawItems = (List<?>) ((Map<?, ?>) raw).get("items");
        assertEquals(1, ((Number) rawItems.get(0)).intValue());
        assertEquals(true, rawItems.get(1));
    }

    @Test
    void bindsPojoUnknownFieldsCreatorsAndAliases() {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);
        Plain pojo = (Plain) binding.readNode("{\"name\":\"han\",\"initial\":\"\",\"unknown\":{\"x\":[1]}}", Plain.class);
        assertEquals("han", pojo.name);
        assertNull(pojo.initial);

        Created created = (Created) binding.readNode("{\"n\":\"han\",\"city\":\"sh\"}", Created.class);
        assertEquals("han", created.name);
        assertEquals(0, created.age);
        assertEquals("sh", created.city);
        Map<?, ?> written = cast(binding.readNode(binding.writeNodeAsString(created), Map.class));
        assertEquals("han", written.get("name"));
        assertEquals(0, ((Number) written.get("age")).intValue());
        assertEquals("sh", written.get("city"));
    }

    @Test
    void honorsDynamicFlagsAndNullInclusion() {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);
        ReadStatic readStatic = (ReadStatic) binding.readNode("{\"id\":1,\"extra\":2}", ReadStatic.class);
        assertNull(readStatic.getNode("extra"));
        WriteStatic writeStatic = (WriteStatic) binding.readNode("{\"id\":1,\"extra\":2}", WriteStatic.class);
        assertEquals(2, writeStatic.getInt("extra"));
        Map<?, ?> staticOutput = cast(binding.readNode(binding.writeNodeAsString(writeStatic), Map.class));
        assertEquals(Set.of("id"), staticOutput.keySet());
        assertEquals(1, ((Number) staticOutput.get("id")).intValue());

        Map<String, Object> nullable = new LinkedHashMap<>();
        nullable.put("keep", 1);
        nullable.put("drop", null);
        Map<?, ?> included = cast(binding.readNode(binding.writeNodeAsString(nullable), Map.class));
        assertTrue(included.containsKey("drop"));
        assertNull(included.get("drop"));
        JsonBinder<?, ?> nullOmittingBinding = binding(new StreamingContext(false));
        Map<?, ?> omitted = cast(nullOmittingBinding.readNode(
                nullOmittingBinding.writeNodeAsString(nullable), Map.class));
        assertEquals(Set.of("keep"), omitted.keySet());
        assertEquals(1, ((Number) omitted.get("keep")).intValue());
    }

    @Test
    void bindsOneOfByDiscriminatorAndJsonType() {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);
        Zoo zoo = (Zoo) binding.readNode("{\"pet\":{\"kind\":\"cat\",\"name\":\"Mimi\",\"lives\":9}}", Zoo.class);
        Cat cat = assertInstanceOf(Cat.class, zoo.pet);
        assertEquals(9, cat.lives);
        assertInstanceOf(PolyObject.class, binding.readNode("{\"a\":1}", Poly.class));
        assertInstanceOf(PolyArray.class, binding.readNode("[1,2]", Poly.class));
    }

    @Test
    void skipsUnmappedJsonTypeOneOfValues() {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);
        FallbackHolder holder = (FallbackHolder) binding.readNode("{\"value\":1,\"after\":2}", FallbackHolder.class);
        assertNull(holder.value);
        assertEquals(2, holder.after);
    }

    @Test
    void readsAndWritesValueCodecsWithoutDependingOnJsonFormatting() {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);

        Code code = (Code) binding.readNode("\"alpha\"", Code.class);
        assertEquals("alpha", code.value);
        assertEquals("alpha", binding.readNode(binding.writeNodeAsString(code), String.class));

        CodeHolder holder = (CodeHolder) binding.readNode("{\"code\":\"beta\"}", CodeHolder.class);
        assertEquals("beta", holder.code.value);
        Map<?, ?> written = cast(binding.readNode(binding.writeNodeAsString(holder), Map.class));
        assertEquals("beta", written.get("code"));
    }

    @Test
    void dispatchesCodecAndContainerValuesAtRootsAndFields() {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);

        assertEquals(true, ((BooleanCode) binding.readNode("true", BooleanCode.class)).value);
        assertEquals(12, ((NumberCode) binding.readNode("12", NumberCode.class)).value.intValue());
        assertEquals(12L, binding.readNode("12", Long.class));
        assertEquals(12f, binding.readNode("12", Float.class));
        assertEquals(12d, binding.readNode("12", Double.class));
        assertEquals((short) 12, binding.readNode("12", Short.class));
        assertEquals((byte) 12, binding.readNode("12", Byte.class));
        assertEquals(List.of("a", "b"), ((ListCode) binding.readNode("[\"a\",\"b\"]", ListCode.class)).value);
        assertEquals(Map.of("x", 3), ((MapCode) binding.readNode("{\"x\":3}", MapCode.class)).value);
        assertNull(binding.readNode("null", ListCode.class));

        DispatchHolder holder = (DispatchHolder) binding.readNode(
                "{\"map\":{\"a\":1},\"list\":[2],\"set\":[3,3],\"array\":[4,5]," +
                        "\"object\":{\"nested\":true},\"jsonArray\":[\"x\"],\"code\":false,\"nullable\":null}",
                DispatchHolder.class);
        assertEquals(Map.of("a", 1), holder.map);
        assertEquals(List.of(2), holder.list);
        assertEquals(Set.of(3), holder.set);
        assertArrayEquals(new int[]{4, 5}, holder.array);
        assertTrue(holder.object.getBoolean("nested"));
        assertEquals("x", holder.jsonArray.getString(0));
        assertEquals(false, holder.code.value);
        assertNull(holder.nullable);

        Map<?, ?> written = cast(binding.readNode(binding.writeNodeAsString(holder), Map.class));
        assertEquals(false, written.get("code"));
        assertNull(written.get("nullable"));
        assertEquals(List.of("a", "b"), binding.readNode(binding.writeNodeAsString(new ListCode(List.of("a", "b"))), List.class));
        assertEquals(Map.of("x", 3), binding.readNode(binding.writeNodeAsString(new MapCode(Map.of("x", 3))), Map.class));
    }

    @Test
    void bindsParentScopedOneOfBeforeAndAfterItsDiscriminator() {
        JsonBinder<?, ?> binding = binding(StreamingContext.EMPTY);

        ParentZoo deferred = (ParentZoo) binding.readNode(
                "{\"pet\":{\"name\":\"Mimi\",\"lives\":9},\"kind\":\"cat\"}", ParentZoo.class);
        Cat deferredCat = assertInstanceOf(Cat.class, deferred.pet);
        assertEquals("Mimi", deferredCat.name);
        assertEquals(9, deferredCat.lives);

        ParentZoo afterDiscriminator = (ParentZoo) binding.readNode(
                "{\"kind\":\"dog\",\"pet\":{\"name\":\"Rex\",\"bark\":3}}", ParentZoo.class);
        Dog afterDiscriminatorDog = assertInstanceOf(Dog.class, afterDiscriminator.pet);
        assertEquals("Rex", afterDiscriminatorDog.name);
        assertEquals(3, afterDiscriminatorDog.bark);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) { return (T) value; }

    enum Kind { FIRST }
    static class Plain { public String name; public Character initial; }
    static class Created {
        final String name;
        final int age;
        String city;
        @NodeCreator Created(@NodeProperty(value = "name", aliases = "n") String name,
                             @NodeProperty("age") int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public int getAge() { return age; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }
    @NodeBinding(readDynamic = false) static class ReadStatic extends JsonObject { public int id; }
    @NodeBinding(writeDynamic = false) static class WriteStatic extends JsonObject { public int id; }
    @OneOf(value = {@OneOf.Mapping(value = Cat.class, when = "cat"), @OneOf.Mapping(value = Dog.class, when = "dog")}, key = "kind")
    static class Animal { public String kind; public String name; }
    static class Cat extends Animal { public int lives; }
    static class Dog extends Animal { public int bark; }
    static class Zoo { public Animal pet; }
    static class ParentZoo {
        public String kind;
        @OneOf(value = {@OneOf.Mapping(value = Cat.class, when = "cat"), @OneOf.Mapping(value = Dog.class, when = "dog")},
                key = "kind", scope = OneOf.Scope.PARENT)
        public Animal pet;
    }
    @NodeValue static class Code {
        final String value;
        Code(String value) { this.value = value; }
        @ValueToRaw String encode() { return value; }
        @RawToValue static Code decode(String raw) { return new Code(raw); }
    }
    static class CodeHolder { public Code code; }
    @NodeValue static class BooleanCode {
        final boolean value;
        BooleanCode(boolean value) { this.value = value; }
        @ValueToRaw boolean encode() { return value; }
        @RawToValue static BooleanCode decode(boolean raw) { return new BooleanCode(raw); }
    }
    @NodeValue static class NumberCode {
        final Number value;
        NumberCode(Number value) { this.value = value; }
        @ValueToRaw Number encode() { return value; }
        @RawToValue static NumberCode decode(Number raw) { return new NumberCode(raw); }
    }
    @NodeValue static class ListCode {
        final List<String> value;
        ListCode(List<String> value) { this.value = value; }
        @ValueToRaw List<String> encode() { return value; }
        @RawToValue static ListCode decode(List<String> raw) { return new ListCode(raw); }
    }
    @NodeValue static class MapCode {
        final Map<String, Integer> value;
        MapCode(Map<String, Integer> value) { this.value = value; }
        @ValueToRaw Map<String, Integer> encode() { return value; }
        @RawToValue static MapCode decode(Map<String, Integer> raw) { return new MapCode(raw); }
    }
    static class DispatchHolder {
        public Map<String, Integer> map;
        public List<Integer> list;
        public Set<Integer> set;
        public int[] array;
        public JsonObject object;
        public JsonArray jsonArray;
        public BooleanCode code;
        public List<String> nullable;
    }
    @OneOf({@OneOf.Mapping(PolyObject.class), @OneOf.Mapping(PolyArray.class)}) interface Poly { }
    static class PolyObject extends JsonObject implements Poly { }
    static class PolyArray extends JsonArray implements Poly { }
    @OneOf(value = {@OneOf.Mapping(FallbackObject.class)}, onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL)
    interface FallbackPoly { }
    static class FallbackObject extends JsonObject implements FallbackPoly { }
    static class FallbackHolder { public FallbackPoly value; public int after; }
}
