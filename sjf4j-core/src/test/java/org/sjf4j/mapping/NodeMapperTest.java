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
        profile.aliases = Arrays.asList("Ada", "A");
        profile.scores = new LinkedHashSet<>(Arrays.asList(1, 2));
        profile.ranks = new int[]{3, 5};
        profile.details = JsonObject.of("address", JsonObject.of("city", "Oslo"));
        profile.status = Status.OK;
        return profile;
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
