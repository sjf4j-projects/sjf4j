package org.sjf4j.mapping;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.BindingException;
import org.sjf4j.fixture.JsonObjectPersonFixture.Baby;
import org.sjf4j.fixture.JsonObjectPersonFixture.Person;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
class NodeMapperTest {

    @Test
    void convertClassHandlesNullScalarsAndEnums() {
        assertNull(NodeMapper.convert(null, String.class, false));
        assertEquals(Integer.valueOf(12), NodeMapper.convert(12L, Integer.class, false));
        assertEquals(Character.valueOf('a'), NodeMapper.convert("abc", Character.class, false));
        assertEquals("a", NodeMapper.convert('a', String.class, false));
        assertEquals(Boolean.TRUE, NodeMapper.convert(true, Boolean.class, false));
        assertEquals(Status.OK, NodeMapper.convert("OK", Status.class, false));
        assertEquals("OK", NodeMapper.convert(Status.OK, String.class, false));

        assertEquals(Integer.valueOf(7),
                NodeMapper.convert(7L, Integer.class, false, RuntimeContext.EMPTY));
    }

    @Test
    void convertTypeAndTypeReferenceHandleTopLevelGenerics() {
        TypeReference<List<User>> reference = new TypeReference<List<User>>() {};
        JsonArray node = JsonArray.of(
                JsonObject.of("name", "A", "age", 7),
                JsonObject.of("name", "B", "age", 8));

        List<User> fromReference = (List<User>) NodeMapper.convert(node, reference.getType(), false);
        List<User> fromReferenceWithContext =
                (List<User>) NodeMapper.convert(node, reference.getType(), false, RuntimeContext.EMPTY);
        Type type = reference.getType();
        List<User> fromType = (List<User>) NodeMapper.convert(node, type, false, RuntimeContext.EMPTY);
        List<User> fromTypeWithContext =
                (List<User>) NodeMapper.convert(node, type, false, RuntimeContext.EMPTY);

        assertEquals("A", fromReference.get(0).name);
        assertEquals(8, fromReferenceWithContext.get(1).age);
        assertInstanceOf(User.class, fromType.get(0));
        assertEquals("B", fromTypeWithContext.get(1).name);
    }

    @Test
    void convertHandlesNestedGenericPojo() {
        JsonObject node = JsonObject.of(
                "code", 200,
                "body", JsonArray.of(
                        JsonObject.of("name", "A", "age", 7),
                        JsonObject.of("name", "B", "age", 8)));

        Envelope<List<User>> result = (Envelope<List<User>>) NodeMapper.convert(node,
                new TypeReference<Envelope<List<User>>>() {}.getType(), false);

        assertEquals(200, result.code);
        assertInstanceOf(User.class, result.body.get(0));
        assertEquals("B", result.body.get(1).name);
    }

    @Test
    void convertHandlesPojoMapArraySetAndJsonNodeStructures() {
        Profile source = profile();

        Map<String, Object> map = (Map<String, Object>) NodeMapper.convert(source,
                new TypeReference<Map<String, Object>>() {}.getType(), false);
        JsonObject json = (JsonObject) NodeMapper.convert(map, JsonObject.class, false);
        Profile rebuilt = (Profile) NodeMapper.convert(json, Profile.class, false);
        String[] names = (String[]) NodeMapper.convert(JsonArray.of("A", "B"), String[].class, false);
        Set<Integer> values = (Set<Integer>) NodeMapper.convert(new int[]{1, 2, 2},
                new TypeReference<Set<Integer>>() {}.getType(), false);
        List<String> ordered = (List<String>) NodeMapper.convert(
                new LinkedHashSet<>(Arrays.asList("first", "second")),
                new TypeReference<List<String>>() {}.getType(), false);

        assertEquals("Ada", map.get("name"));
        assertInstanceOf(JsonObject.class, json);
        assertEquals("Oslo", rebuilt.details.getJsonObject("address").getString("city"));
        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 2)), rebuilt.scores);
        assertArrayEquals(new String[]{"A", "B"}, names);
        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 2)), values);
        assertEquals(Arrays.asList("first", "second"), ordered);
    }

    @Test
    void convertsListJsonArrayAndJavaArraysWithoutIndexedAdapters() {
        Type integers = new TypeReference<List<Integer>>() {}.getType();
        Type longSet = new TypeReference<Set<Long>>() {}.getType();

        Object[] sources = {
                Arrays.asList(1, 2, 2),
                JsonArray.of(1, 2, 2),
                new int[]{1, 2, 2},
                new Integer[]{1, 2, 2}
        };

        for (Object source : sources) {
            List<Integer> list = (List<Integer>) NodeMapper.convert(source, integers, false);
            assertEquals(Arrays.asList(1, 2, 2), list);

            JsonArray jsonArray = (JsonArray) NodeMapper.convert(source, JsonArray.class, false);
            assertEquals(3, jsonArray.size());
            assertEquals(2, jsonArray.getNode(2));

            IntJajo jajo = (IntJajo) NodeMapper.convert(source, IntJajo.class, false);
            assertEquals(3, jajo.size());
            assertEquals(2, jajo.getNode(1));

            int[] array = (int[]) NodeMapper.convert(source, int[].class, false);
            assertArrayEquals(new int[]{1, 2, 2}, array);

            Set<Long> set = (Set<Long>) NodeMapper.convert(source, longSet, false);
            assertEquals(new LinkedHashSet<>(Arrays.asList(1L, 2L)), set);
        }
    }

    @Test
    void indexedConversionPreservesNullsAndNestedErrorPaths() {
        for (Object source : new Object[]{
                Arrays.asList(1, null),
                JsonArray.of(1, null),
                new Integer[]{1, null}
        }) {
            List<Integer> list = (List<Integer>) NodeMapper.convert(
                    source, new TypeReference<List<Integer>>() {}.getType(), false);
            assertEquals(Arrays.asList(1, null), list);
        }

        for (Object source : new Object[]{
                Arrays.asList(1, true),
                JsonArray.of(1, true),
                new Object[]{1, true}
        }) {
            BindingException error = assertThrows(BindingException.class,
                    () -> NodeMapper.convert(source, new TypeReference<List<Integer>>() {}.getType(), false));
            assertTrue(error.getMessage().contains("$[1]"));
        }
    }

    @Test
    void indexedLongAndObjectArrayFastPathsPreserveMappingSemantics() {
        Type integers = new TypeReference<List<Integer>>() {}.getType();
        Type longs = new TypeReference<List<Long>>() {}.getType();

        for (Object source : new Object[]{
                new long[]{1, 2, 3},
                new Long[]{1L, 2L, 3L},
                new int[]{1, 2, 3},
                new Integer[]{1, 2, 3}
        }) {
            assertEquals(Arrays.asList(1, 2, 3),
                    NodeMapper.convert(source, integers, false));
            assertEquals(Arrays.asList(1L, 2L, 3L),
                    NodeMapper.convert(source, longs, true));
            assertArrayEquals(new int[]{1, 2, 3},
                    (int[]) NodeMapper.convert(source, int[].class, false));
        }

        assertEquals(Arrays.asList(1, null, 3),
                NodeMapper.convert(new Long[]{1L, null, 3L}, integers, false));

        BindingException overflow = assertThrows(BindingException.class,
                () -> NodeMapper.convert(
                        new long[]{1L, ((long) Integer.MAX_VALUE) + 1},
                        integers, false));
        assertTrue(overflow.getMessage().contains("$[1]"));

        BindingException shortOverflow = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new int[]{1, Short.MAX_VALUE + 1},
                        new TypeReference<List<Short>>() {}.getType(), false));
        assertTrue(shortOverflow.getMessage().contains("$[1]"));

        BindingException incompatible = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new Object[]{1, true}, integers, false));
        assertTrue(incompatible.getMessage().contains("$[1]"));
    }

    @Test
    void indexedPrimitiveArrayFastPathsCoverEveryElementType() {
        Type ints = new TypeReference<List<Integer>>() {}.getType();
        Type doubles = new TypeReference<List<Double>>() {}.getType();
        Type floats = new TypeReference<List<Float>>() {}.getType();
        Type booleans = new TypeReference<List<Boolean>>() {}.getType();
        Type characters = new TypeReference<List<Character>>() {}.getType();
        Type strings = new TypeReference<List<String>>() {}.getType();

        assertEquals(Arrays.asList(1, 2, 3),
                NodeMapper.convert(new byte[]{1, 2, 3}, ints, false));
        assertEquals(Arrays.asList(1, 2, 3),
                NodeMapper.convert(new short[]{1, 2, 3}, ints, false));
        assertEquals(Arrays.asList(1.25d, 2.5d),
                NodeMapper.convert(new float[]{1.25f, 2.5f}, doubles, false));
        assertEquals(Arrays.asList(1.25f, 2.5f),
                NodeMapper.convert(new double[]{1.25d, 2.5d}, floats, false));
        assertEquals(Arrays.asList(true, false, true),
                NodeMapper.convert(new boolean[]{true, false, true}, booleans, false));
        assertEquals(Arrays.asList('A', 'B'),
                NodeMapper.convert(new char[]{'A', 'B'}, characters, false));
        assertEquals(Arrays.asList("A", "B"),
                NodeMapper.convert(new char[]{'A', 'B'}, strings, false));

        // Direct primitive reads must keep indexed paths on checked numeric failures.
        BindingException overflow = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new double[]{1.0, Double.MAX_VALUE}, floats, false));
        assertTrue(overflow.getMessage().contains("$[1]"));

        // Non-List targets still use the same generic target-conversion engine.
        assertArrayEquals(new int[]{1, 2},
                (int[]) NodeMapper.convert(new short[]{1, 2}, int[].class, false));
        assertEquals(Arrays.asList(true, false),
                NodeMapper.convert(new boolean[]{true, false},
                        new TypeReference<List<Boolean>>() {}.getType(), true));
    }

    @Test
    void convertHandlesEmptyContainersAndNullMembers() {
        Profile empty = (Profile) NodeMapper.convert(JsonObject.of(
                "aliases", JsonArray.of(),
                "scores", JsonArray.of(),
                "ranks", JsonArray.of()), Profile.class, false);
        Profile nullable = (Profile) NodeMapper.convert(JsonObject.of(
                "name", null,
                "aliases", JsonArray.of("Ada", null),
                "details", null), Profile.class, false);

        assertTrue(empty.aliases.isEmpty());
        assertTrue(empty.scores.isEmpty());
        assertArrayEquals(new int[0], empty.ranks);
        assertNull(nullable.name);
        assertEquals(Arrays.asList("Ada", null), nullable.aliases);
        assertNull(nullable.details);
    }

    @Test
    void cachesPojoPropertyBindingsWithoutLosingCreatorOrJojoDynamicFields() {
        SourceWithExtras source = new SourceWithExtras();
        source.name = "Ada";
        source.age = 32;
        source.unused = "extra";

        // Both conversions exercise the same cached concrete class pair.
        for (int i = 0; i < 2; i++) {
            NameOnly only = (NameOnly) NodeMapper.convert(source, NameOnly.class, false);
            assertEquals("Ada", only.name);

            CreatorTarget creator = (CreatorTarget) NodeMapper.convert(
                    source, CreatorTarget.class, false);
            assertEquals("Ada", creator.name);
            assertEquals(32, creator.age);

            OpenTarget open = (OpenTarget) NodeMapper.convert(source, OpenTarget.class, false);
            assertEquals("Ada", open.name);
            assertEquals(32, open.getNode("age"));
            assertEquals("extra", open.getNode("unused"));
            assertTrue(!open.dynamicProperties().containsKey("name"));
        }
    }

    @Test
    void mapsCreatorAndJojoPropertiesConsistentlyAcrossObjectSources() {
        SourceWithExtras pojo = new SourceWithExtras();
        pojo.name = "Ada";
        pojo.age = 32;
        pojo.unused = "extra";

        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("name", "Ada");
        map.put("age", 32);
        map.put("unused", "extra");

        // POJO uses the cached source-property plan; Map and JsonObject
        // traverse entries. All must apply identical Creator/JOJO semantics.
        for (Object source : new Object[]{pojo, map, new JsonObject(map)}) {
            CreatorTarget creator = (CreatorTarget) NodeMapper.convert(source, CreatorTarget.class, false);
            assertEquals("Ada", creator.name);
            assertEquals(32, creator.age);

            OpenTarget open = (OpenTarget) NodeMapper.convert(source, OpenTarget.class, false);
            assertEquals("Ada", open.name);
            assertEquals(32, open.getNode("age"));
            assertEquals("extra", open.getNode("unused"));
            assertTrue(!open.dynamicProperties().containsKey("name"));
        }
    }

    @Test
    void usesNoArgsCreatorForMapPojoAndJojoWithoutLosingFields() {
        CountingTarget.created = 0;

        SourceWithExtras source = new SourceWithExtras();
        source.name = "Ada";
        source.age = 32;
        source.unused = "extra";

        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("name", "Ada");
        map.put("age", 32);
        map.put("unused", "extra");

        for (Object input : new Object[]{source, map, new JsonObject(map)}) {
            CountingTarget target = (CountingTarget) NodeMapper.convert(input, CountingTarget.class, false);
            assertEquals("Ada", target.name);
            assertEquals(32, target.age);
            assertEquals("extra", target.unused);
        }
        assertEquals(3, CountingTarget.created);

        // The same direct-creation path must support JOJO's static properties
        // and dynamically stored keys.
        OpenTarget jojo = (OpenTarget) NodeMapper.convert(map, OpenTarget.class, false);
        assertEquals("Ada", jojo.name);
        assertEquals(32, jojo.getNode("age"));
        assertEquals("extra", jojo.getNode("unused"));
    }

    @Test
    void copyRecreatesNestedMutableNodesWhileConvertPreservesIdentity() {
        Profile source = profile();

        assertSame(source, NodeMapper.convert(source, Profile.class, false));

        Profile copied = (Profile) NodeMapper.convert(source, source.getClass(), true);

        assertNotSame(source, copied);
        assertNotSame(source.aliases, copied.aliases);
        assertNotSame(source.scores, copied.scores);
        assertNotSame(source.ranks, copied.ranks);
        assertNotSame(source.details, copied.details);

        copied.aliases.add("new");
        copied.details.getJsonObject("address").put("city", "Rome");
        copied.ranks[0] = 99;
        assertEquals(Arrays.asList("Ada", "A"), source.aliases);
        assertEquals("Oslo", source.details.getJsonObject("address").getString("city"));
        assertArrayEquals(new int[]{3, 5}, source.ranks);
    }

    @Test
    void convertToRawProducesDetachedJsonCompatibleValues() {
        Person person = new Person();
        person.setName("Lily");
        person.setAge(22);
        person.setInfo(JsonObject.of("city", "Oslo"));
        person.setBabies(Arrays.asList(new Baby("A", 6)));
        person.put("nickname", "Li");

        Map<String, Object> raw = (Map<String, Object>) NodeMapper.convertToRaw(person, RuntimeContext.EMPTY);
        Map<String, Object> rawWithContext =
                (Map<String, Object>) NodeMapper.convertToRaw(person, RuntimeContext.EMPTY);

        assertEquals("Lily", raw.get("name"));
        assertEquals("Li", raw.get("nickname"));
        assertInstanceOf(Map.class, raw.get("info"));
        assertInstanceOf(List.class, raw.get("babies"));
        Map<String, Object> rawInfo = (Map<String, Object>) raw.get("info");
        Map<String, Object> rawBaby =
                (Map<String, Object>) ((List<Object>) raw.get("babies")).get(0);
        assertEquals("Oslo", rawInfo.get("city"));
        assertEquals("A", rawBaby.get("name"));
        assertEquals(6, rawBaby.get("month"));
        assertEquals(raw, rawWithContext);
        assertNotSame(person.getInfo(), raw.get("info"));

        rawInfo.put("city", "Rome");
        rawBaby.put("name", "B");
        assertEquals("Oslo", person.getInfo().getString("city"));
        assertEquals("A", person.getBabies().get(0).getName());
    }

    @Test
    void convertReportsTheNestedPathForAnIncompatibleScalar() {
        BindingException error = assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonArray.of(1, true),
                        new TypeReference<List<Integer>>() {}.getType(), false));

        assertTrue(error.hasPathSegment());
        assertTrue(error.getMessage().contains("$[1]"));
    }

    @Test
    void convertDecodesNullWithNodeValue() {
        NullValue root = (NullValue) NodeMapper.convert(null, NullValue.class, false);
        NullValueHolder holder = (NullValueHolder) NodeMapper.convert(JsonObject.of("value", null), NullValueHolder.class, false);

        assertEquals("<null>", root.value);
        assertEquals("<null>", holder.value.value);
    }

    @Test
    void convertsBetweenNodeValuesUsingCompatibleRawRepresentations() {
        EncodedA source = new EncodedA("hello");

        EncodedB converted = (EncodedB) NodeMapper.convert(source, EncodedB.class, false);
        assertEquals("hello", converted.value);

        // No implicit String -> Number coercion at a ValueCodec boundary.
        BindingException error = assertThrows(BindingException.class,
                () -> NodeMapper.convert(source, NumericValue.class, false));
        assertTrue(error.getMessage().contains("raw type"));

        assertSame(source, NodeMapper.convert(source, EncodedA.class, false));
    }

    @Test
    void convertsNodeValueMembersOfPojoAndMapWithoutDroppingRawEncoding() {
        SourceValueHolder source = new SourceValueHolder();
        source.code = new EncodedA("abc");

        TargetValueHolder fromPojo = (TargetValueHolder) NodeMapper.convert(
                source, TargetValueHolder.class, false);
        assertEquals("abc", fromPojo.code.value);

        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("code", new EncodedA("xyz"));
        TargetValueHolder fromMap = (TargetValueHolder) NodeMapper.convert(
                values, TargetValueHolder.class, false);
        assertEquals("xyz", fromMap.code.value);
    }

    @NodeValue
    static class EncodedA {
        final String value;
        EncodedA(String value) { this.value = value; }

        @ValueToRaw String toRaw() { return value; }
        @RawToValue static EncodedA fromRaw(String value) { return new EncodedA(value); }
    }

    @NodeValue
    static class EncodedB {
        final String value;
        EncodedB(String value) { this.value = value; }

        @ValueToRaw String toRaw() { return value; }
        @RawToValue static EncodedB fromRaw(String value) { return new EncodedB(value); }
    }

    @NodeValue
    static class NumericValue {
        final Long value;
        NumericValue(Long value) { this.value = value; }

        @ValueToRaw Long toRaw() { return value; }
        @RawToValue static NumericValue fromRaw(Long value) { return new NumericValue(value); }
    }

    static class SourceValueHolder {
        public EncodedA code;
    }

    static class TargetValueHolder {
        public EncodedB code;
    }

    private static Profile profile() {
        Profile profile = new Profile();
        profile.name = "Ada";
        profile.age = 32;
        profile.aliases = new java.util.ArrayList<>(Arrays.asList("Ada", "A"));
        profile.scores = new LinkedHashSet<>(Arrays.asList(1, 2));
        profile.ranks = new int[]{3, 5};
        profile.details = JsonObject.of("address", JsonObject.of("city", "Oslo"));
        profile.status = Status.OK;
        return profile;
    }

    public static class IntJajo extends JsonArray {
        @Override
        public Class<?> elementClass() {
            return Integer.class;
        }
    }

    static class CountingTarget {
        static int created;
        public String name;
        public int age;
        public String unused;

        CountingTarget() {
            created++;
        }
    }

    static class SourceWithExtras {
        public String name;
        public int age;
        public String unused;
    }

    static class NameOnly {
        public String name;
    }

    static class OpenTarget extends JsonObject {
        public String name;
    }

    static class CreatorTarget {
        public final String name;
        public final int age;

        @org.sjf4j.annotation.node.NodeCreator
        public CreatorTarget(@org.sjf4j.annotation.node.NodeProperty("name") String name,
                      @org.sjf4j.annotation.node.NodeProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    enum Status {
        OK,
        FAILED
    }

    static class User {
        public String name;
        public int age;
    }

    static class Envelope<T> {
        public int code;
        public T body;
    }

    @NodeValue
    static class NullValue {
        final String value;

        NullValue(String value) {
            this.value = value;
        }

        @ValueToRaw String encode() {
            return value;
        }

        @RawToValue static NullValue decode(String raw) {
            return new NullValue(raw == null ? "<null>" : raw);
        }
    }

    static class NullValueHolder {
        public NullValue value;
    }

    static class Profile {
        public String name;
        public int age;
        public List<String> aliases;
        public Set<Integer> scores;
        public int[] ranks;
        public JsonObject details;
        public Status status;
    }
}
