package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.fixture.JsonObjectPersonFixture.Baby;
import org.sjf4j.fixture.JsonObjectPersonFixture.Person;
import org.sjf4j.node.NodeMapper;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sjf4j.node.NodeMapperFixtures.*;

@SuppressWarnings("unchecked")
class NodeMapperConversionTest {

    @Test
    void convertClassHandlesNullScalarsAndEnums() {
        assertNull(NodeMapper.convert(null, String.class, false, RuntimeContext.EMPTY));
        assertEquals(Integer.valueOf(12), NodeMapper.convert(12L, Integer.class, false, RuntimeContext.EMPTY));
        assertEquals(Character.valueOf('a'), NodeMapper.convert("a", Character.class, false, RuntimeContext.EMPTY));
        assertThrows(BindingException.class, () -> NodeMapper.convert("abc", Character.class, false, RuntimeContext.EMPTY));
        assertThrows(BindingException.class, () -> NodeMapper.convert("", Character.class, false, RuntimeContext.EMPTY));
        assertEquals("a", NodeMapper.convert('a', String.class, false, RuntimeContext.EMPTY));
        assertEquals(Boolean.TRUE, NodeMapper.convert(true, Boolean.class, false, RuntimeContext.EMPTY));
        assertEquals(Status.OK, NodeMapper.convert("OK", Status.class, false, RuntimeContext.EMPTY));
        assertEquals("OK", NodeMapper.convert(Status.OK, String.class, false, RuntimeContext.EMPTY));
        assertNull(NodeMapper.convert(null, void.class, false, RuntimeContext.EMPTY));
        assertThrows(BindingException.class, () -> NodeMapper.convert("x", Void.class, false, RuntimeContext.EMPTY));

        assertEquals(Integer.valueOf(7),
                NodeMapper.convert(7L, Integer.class, false, RuntimeContext.EMPTY));
    }

    @Test
    void convertTypeAndTypeReferenceHandleTopLevelGenerics() {
        TypeReference<List<User>> reference = new TypeReference<List<User>>() {};
        JsonArray node = JsonArray.of(
                JsonObject.of("name", "A", "age", 7),
                JsonObject.of("name", "B", "age", 8));

        List<User> fromReference = (List<User>) NodeMapper.convert(node, reference.getType(), false, RuntimeContext.EMPTY);
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
                new TypeReference<Envelope<List<User>>>() {}.getType(), false, RuntimeContext.EMPTY);

        assertEquals(200, result.code);
        assertInstanceOf(User.class, result.body.get(0));
        assertEquals("B", result.body.get(1).name);
    }

    @Test
    void convertHandlesPojoMapArraySetAndJsonNodeStructures() {
        Profile source = profile();

        Map<String, Object> map = (Map<String, Object>) NodeMapper.convert(source,
                new TypeReference<Map<String, Object>>() {}.getType(), false, RuntimeContext.EMPTY);
        JsonObject json = (JsonObject) NodeMapper.convert(map, JsonObject.class, false, RuntimeContext.EMPTY);
        Profile rebuilt = (Profile) NodeMapper.convert(json, Profile.class, false, RuntimeContext.EMPTY);
        String[] names = (String[]) NodeMapper.convert(JsonArray.of("A", "B"), String[].class, false, RuntimeContext.EMPTY);
        Set<Integer> values = (Set<Integer>) NodeMapper.convert(new int[]{1, 2, 2},
                new TypeReference<Set<Integer>>() {}.getType(), false, RuntimeContext.EMPTY);
        List<String> ordered = (List<String>) NodeMapper.convert(
                new LinkedHashSet<>(Arrays.asList("first", "second")),
                new TypeReference<List<String>>() {}.getType(), false, RuntimeContext.EMPTY);

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
                "ranks", JsonArray.of()), Profile.class, false, RuntimeContext.EMPTY);
        Profile nullable = (Profile) NodeMapper.convert(JsonObject.of(
                "name", null,
                "aliases", JsonArray.of("Ada", null),
                "details", null), Profile.class, false, RuntimeContext.EMPTY);

        assertTrue(empty.aliases.isEmpty());
        assertTrue(empty.scores.isEmpty());
        assertArrayEquals(new int[0], empty.ranks);
        assertNull(nullable.name);
        assertEquals(Arrays.asList("Ada", null), nullable.aliases);
        assertNull(nullable.details);
    }

    @Test
    void mapsPojoPropertiesWithoutLosingCreatorOrJojoDynamicFields() {
        SourceWithExtras source = new SourceWithExtras();
        source.name = "Ada";
        source.age = 32;
        source.unused = "extra";

        // Repeated conversions preserve Creator and JOJO dynamic fields.
        for (int i = 0; i < 2; i++) {
            NameOnly only = (NameOnly) NodeMapper.convert(source, NameOnly.class, false, RuntimeContext.EMPTY);
            assertEquals("Ada", only.name);

            CreatorTarget creator = (CreatorTarget) NodeMapper.convert(
                    source, CreatorTarget.class, false, RuntimeContext.EMPTY);
            assertEquals("Ada", creator.name);
            assertEquals(32, creator.age);

            OpenTarget open = (OpenTarget) NodeMapper.convert(source, OpenTarget.class, false, RuntimeContext.EMPTY);
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

        // POJO reads cached per-class metadata; Map and JsonObject traverse
        // entries. All must apply identical Creator/JOJO semantics.
        for (Object source : new Object[]{pojo, map, new JsonObject(map)}) {
            CreatorTarget creator = (CreatorTarget) NodeMapper.convert(source, CreatorTarget.class, false, RuntimeContext.EMPTY);
            assertEquals("Ada", creator.name);
            assertEquals(32, creator.age);

            OpenTarget open = (OpenTarget) NodeMapper.convert(source, OpenTarget.class, false, RuntimeContext.EMPTY);
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
            CountingTarget target = (CountingTarget) NodeMapper.convert(input, CountingTarget.class, false, RuntimeContext.EMPTY);
            assertEquals("Ada", target.name);
            assertEquals(32, target.age);
            assertEquals("extra", target.unused);
        }
        assertEquals(3, CountingTarget.created);

        // The same direct-creation path must support JOJO's static properties
        // and dynamically stored keys.
        OpenTarget jojo = (OpenTarget) NodeMapper.convert(map, OpenTarget.class, false, RuntimeContext.EMPTY);
        assertEquals("Ada", jojo.name);
        assertEquals(32, jojo.getNode("age"));
        assertEquals("extra", jojo.getNode("unused"));
    }

    @Test
    void valueCodecCopyAndRawBoundaries() {
        StringChoice value = new StringChoice("hello");
        assertSame(value, NodeMapper.convert(value, StringChoice.class, false, RuntimeContext.EMPTY));
        StringChoice copied = (StringChoice) NodeMapper.convert(value, StringChoice.class, true, RuntimeContext.EMPTY);
        assertNotSame(value, copied);
        assertEquals("hello", copied.value);

        assertEquals("hello", ((EncodedB) NodeMapper.convert(
                new EncodedA("hello"), EncodedB.class, true, RuntimeContext.EMPTY)).value);
        assertEquals("hello", NodeMapper.convertToRaw(value, RuntimeContext.EMPTY));
        assertEquals("<null>", ((NullValue) NodeMapper.convert(
                null, NullValue.class, true, RuntimeContext.EMPTY)).value);
        BindingException incompatible = assertThrows(BindingException.class, () ->
                NodeMapper.convert(new EncodedA("7"), NumericValue.class, false, RuntimeContext.EMPTY));
        assertTrue(incompatible.getMessage().contains("no ValueCodec"));
    }

    @Test
    void incompatibleStructuralTargetsAndNestedMembersReportPaths() {
        BindingException member = assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonObject.of("body", JsonArray.of(JsonObject.of("age", true))),
                        new TypeReference<Envelope<List<User>>>() {}.getType(), false, RuntimeContext.EMPTY));
        assertTrue(member.getMessage().contains("$.body[0].age"));

        BindingException mapValue = assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonObject.of("first", JsonArray.of(1, true)),
                        new TypeReference<Map<String, List<Integer>>>() {}.getType(), false, RuntimeContext.EMPTY));
        assertTrue(mapValue.getMessage().contains("$.first[1]"));

        assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonArray.of(1), Map.class, false, RuntimeContext.EMPTY));
        assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonObject.of("a", 1), List.class, false, RuntimeContext.EMPTY));
    }

    @Test
    void constructsRequestedContainersAndRejectsUnsupportedBuilders() {
        JsonObject object = JsonObject.of("b", 2, "a", 1);
        TreeMap<String, Integer> sorted = (TreeMap<String, Integer>) NodeMapper.convert(
                object, new TypeReference<TreeMap<String, Integer>>() {}.getType(), false, RuntimeContext.EMPTY);
        assertEquals(Arrays.asList("a", "b"), new ArrayList<>(sorted.keySet()));

        LinkedList<Long> linked = (LinkedList<Long>) NodeMapper.convert(
                JsonArray.of(2, 1), new TypeReference<LinkedList<Long>>() {}.getType(), false, RuntimeContext.EMPTY);
        assertEquals(Arrays.asList(2L, 1L), linked);

        TreeSet<Integer> set = (TreeSet<Integer>) NodeMapper.convert(
                JsonArray.of(2, 1, 2), new TypeReference<TreeSet<Integer>>() {}.getType(), false, RuntimeContext.EMPTY);
        assertEquals(Arrays.asList(1, 2), new ArrayList<>(set));

        BindingException nullValue = assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonObject.of("missing", null), ConcurrentHashMap.class, false, RuntimeContext.EMPTY));
        assertInstanceOf(NullPointerException.class, nullValue.getCause());
        assertThrows(BindingException.class, () -> NodeMapper.convert(
                JsonArray.of(1), java.util.SortedSet.class, false, RuntimeContext.EMPTY));
        assertThrows(BindingException.class, () -> NodeMapper.convert(
                Arrays.asList("a", "b"), Object.class, true, RuntimeContext.EMPTY));
    }

    @Test
    void convertsSetIntoEachIndexedRepresentationWithElementConversion() {
        Set<Integer> source = new LinkedHashSet<>(Arrays.asList(2, 1));
        Type longs = new TypeReference<List<Long>>() {}.getType();

        assertEquals(Arrays.asList(2L, 1L),
                NodeMapper.convert(source, longs, false, RuntimeContext.EMPTY));
        assertEquals(JsonArray.of(2, 1),
                NodeMapper.convert(source, JsonArray.class, false, RuntimeContext.EMPTY));
        assertArrayEquals(new long[]{2, 1},
                (long[]) NodeMapper.convert(source, long[].class, false, RuntimeContext.EMPTY));
        assertEquals(new LinkedHashSet<>(Arrays.asList(2L, 1L)),
                NodeMapper.convert(source, new TypeReference<Set<Long>>() {}.getType(), false, RuntimeContext.EMPTY));
        IntJajo jajo = (IntJajo) NodeMapper.convert(source, IntJajo.class, false, RuntimeContext.EMPTY);
        assertEquals(2, jajo.getNode(0));

        BindingException error = assertThrows(BindingException.class, () ->
                NodeMapper.convert(new LinkedHashSet<>(Arrays.asList(1, true)),
                        new TypeReference<Set<Integer>>() {}.getType(), false, RuntimeContext.EMPTY));
        assertTrue(error.getMessage().contains("$[1]"));
        assertThrows(BindingException.class, () ->
                NodeMapper.convert(source, Map.class, false, RuntimeContext.EMPTY));
    }

    @Test
    void rawConversionEncodesNestedValuesAndRejectsUnsupportedNodes() {
        JsonObject source = JsonObject.of("values", JsonArray.of(
                new EncodedA("a"), Status.OK, 'x', new int[]{1, 2}));

        Map<String, Object> raw = (Map<String, Object>) NodeMapper.convertToRaw(source, RuntimeContext.EMPTY);
        assertEquals(Arrays.asList("a", "OK", "x", Arrays.asList(1, 2)), raw.get("values"));
        assertNotSame(source.getNode("values"), raw.get("values"));
        assertEquals(Arrays.asList(2, 1), NodeMapper.convertToRaw(
                new LinkedHashSet<>(Arrays.asList(2, 1)), RuntimeContext.EMPTY));

        BindingException error = assertThrows(BindingException.class, () ->
                NodeMapper.convertToRaw(JsonArray.of(new Object()), RuntimeContext.EMPTY));
        assertTrue(error.getMessage().contains("$[0]"));
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
                        new TypeReference<List<Integer>>() {}.getType(), false, RuntimeContext.EMPTY));

        assertTrue(error.hasPathSegment());
        assertTrue(error.getMessage().contains("$[1]"));
    }

    @Test
    void convertDecodesNullWithNodeValue() {
        NullValue root = (NullValue) NodeMapper.convert(null, NullValue.class, false, RuntimeContext.EMPTY);
        NullValueHolder holder = (NullValueHolder) NodeMapper.convert(JsonObject.of("value", null), NullValueHolder.class, false, RuntimeContext.EMPTY);
        List<NullValue> list = (List<NullValue>) NodeMapper.convert(JsonArray.of((Object) null),
                new TypeReference<List<NullValue>>() {}.getType(), false, RuntimeContext.EMPTY);

        assertEquals("<null>", root.value);
        assertEquals("<null>", holder.value.value);
        assertEquals("<null>", list.get(0).value);
    }

    @Test
    void annotatedJsonSubclassesSelectOneOfBeforeStructuralOrIdentityConversion() {
        JsonObject object = JsonObject.of("kind", "child", "name", "A");
        JsonArray array = JsonArray.of(1, 2);

        assertInstanceOf(SelectedObject.class, NodeMapper.convert(object, ObjectChoice.class, false, RuntimeContext.EMPTY));
        assertInstanceOf(SelectedArray.class, NodeMapper.convert(array, ArrayChoice.class, false, RuntimeContext.EMPTY));
        SelectedObject compatibleObject = new SelectedObject();
        compatibleObject.put("kind", "child");
        assertSame(compatibleObject, NodeMapper.convert(compatibleObject, ObjectChoice.class, false, RuntimeContext.EMPTY));
        compatibleObject.put("kind", "unknown");
        assertThrows(BindingException.class,
                () -> NodeMapper.convert(compatibleObject, ObjectChoice.class, false, RuntimeContext.EMPTY));
        SelectedArray compatibleArray = new SelectedArray();
        assertSame(compatibleArray, NodeMapper.convert(compatibleArray, ArrayChoice.class, false, RuntimeContext.EMPTY));
        assertNull(NodeMapper.convert(null, ObjectChoice.class, false, RuntimeContext.EMPTY));
    }

    @Test
    void memberOneOfOverridesClassSelectionAndNullCodec() {
        ChoiceHolder holder = (ChoiceHolder) NodeMapper.convert(
                JsonObject.of("value", JsonObject.of("kind", "child")), ChoiceHolder.class, false, RuntimeContext.EMPTY);
        assertInstanceOf(OtherChoice.class, holder.value);

        ChoiceHolder empty = (ChoiceHolder) NodeMapper.convert(
                JsonObject.of("value", null), ChoiceHolder.class, false, RuntimeContext.EMPTY);
        assertNull(empty.value);

        NullOverrideHolder nullOverride = (NullOverrideHolder) NodeMapper.convert(
                JsonObject.of("value", null), NullOverrideHolder.class, false, RuntimeContext.EMPTY);
        assertNull(nullOverride.value);
    }

    @Test
    void oneOfTreatsUnknownSourcesAsNoMatchWithoutEncodingThem() {
        assertInstanceOf(EncodedChoice.class, NodeMapper.convert(
                Map.of("kind", "child"), ValueChoice.class, false, RuntimeContext.EMPTY));
        assertNull(NodeMapper.convert(Map.of("kind", "missing"), NullableValueChoice.class, false, RuntimeContext.EMPTY));

        BindingException valueBinding = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new EncodedChoice(Map.of("kind", "child")), ValueChoice.class, false, RuntimeContext.EMPTY));
        assertTrue(valueBinding.getMessage().contains("node must be a JSON object"));
        assertNull(NodeMapper.convert(new RawChoice(null), NullableValueChoice.class, false, RuntimeContext.EMPTY));
        BindingException unknown = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new Object(), ValueChoice.class, false, RuntimeContext.EMPTY));
        assertTrue(unknown.getMessage().contains("node must be a JSON object"));

        BindingException failure = assertThrows(BindingException.class,
                () -> NodeMapper.convert(Collections.singletonList(new RawChoice(Map.of("kind", "error"))),
                        new TypeReference<List<ValueChoice>>() {}.getType(), false, RuntimeContext.EMPTY));
        assertTrue(failure.getMessage().contains("$[0]"));
        assertTrue(failure.getMessage().contains("node must be a JSON object"));
    }

    @Test
    void oneOfMatchesRawShapeButRejectsValueBindingSource() {
        StringChoice decoded = (StringChoice) NodeMapper.convert("text", ShapeChoice.class, false, RuntimeContext.EMPTY);
        assertEquals("text", decoded.value);
        BindingException error = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new StringChoice("text"), ShapeChoice.class, false, RuntimeContext.EMPTY));
        assertTrue(error.getMessage().contains("jsonType=UNKNOWN"));
    }

    @Test
    void convertsBetweenNodeValuesUsingCompatibleRawRepresentations() {
        EncodedA source = new EncodedA("hello");

        EncodedB converted = (EncodedB) NodeMapper.convert(source, EncodedB.class, false, RuntimeContext.EMPTY);
        assertEquals("hello", converted.value);

        // No implicit String -> Number coercion at a ValueCodec boundary.
        BindingException error = assertThrows(BindingException.class,
                () -> NodeMapper.convert(source, NumericValue.class, false, RuntimeContext.EMPTY));
        assertTrue(error.getMessage().contains("no ValueCodec"));

        assertSame(source, NodeMapper.convert(source, EncodedA.class, false, RuntimeContext.EMPTY));
    }

    @Test
    void convertsNodeValueMembersOfPojoAndMapWithoutDroppingRawEncoding() {
        SourceValueHolder source = new SourceValueHolder();
        source.code = new EncodedA("abc");

        TargetValueHolder fromPojo = (TargetValueHolder) NodeMapper.convert(
                source, TargetValueHolder.class, false, RuntimeContext.EMPTY);
        assertEquals("abc", fromPojo.code.value);

        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("code", new EncodedA("xyz"));
        TargetValueHolder fromMap = (TargetValueHolder) NodeMapper.convert(
                values, TargetValueHolder.class, false, RuntimeContext.EMPTY);
        assertEquals("xyz", fromMap.code.value);
    }

    @Test
    void directStringAndCharacterAdaptationDistinguishesStrictAndLenient() {
        RuntimeContext context = RuntimeContext.EMPTY;
        assertNull(NodeMapper.toString(null, context));
        assertEquals("text", NodeMapper.toString("text", context));
        assertEquals("x", NodeMapper.toString('x', context));
        assertEquals("OK", NodeMapper.toString(Status.OK, context));
        assertEquals("value", NodeMapper.toString(new EncodedA("value"), context));
        assertThrows(NodeException.class, () -> NodeMapper.toString(new NumericValue(1L), context));
        assertThrows(NodeException.class, () -> NodeMapper.toString(42, context));

        assertNull(NodeMapper.asString(null, context));
        assertEquals("42", NodeMapper.asString(42, context));
        assertEquals("true", NodeMapper.asString(true, context));
        assertEquals("OK", NodeMapper.asString(Status.OK, context));
        assertEquals("7", NodeMapper.asString(new NumericValue(7L), context));

        assertNull(NodeMapper.toChar(null, context));
        assertEquals('x', NodeMapper.toChar('x', context));
        assertEquals('x', NodeMapper.toChar("x", context));
        assertEquals('x', NodeMapper.toChar(new EncodedA("x"), context));
        assertThrows(NodeException.class, () -> NodeMapper.toChar("", context));
        assertThrows(NodeException.class, () -> NodeMapper.toChar("xy", context));
        assertThrows(NodeException.class, () -> NodeMapper.toChar(1, context));

        assertNull(NodeMapper.asChar(null, context));
        assertNull(NodeMapper.asChar("", context));
        assertEquals('x', NodeMapper.asChar('x', context));
        assertEquals('x', NodeMapper.asChar("xyz", context));
        assertEquals('1', NodeMapper.asChar(123, context));
    }

    @Test
    void directEnumAndNumberAdaptationChecksNamesOrdinalsAndRawTypes() {
        RuntimeContext context = RuntimeContext.EMPTY;
        assertNull(NodeMapper.toEnum(null, Status.class, context));
        assertSame(Status.OK, NodeMapper.toEnum(Status.OK, Status.class, context));
        assertSame(Status.FAILED, NodeMapper.toEnum("FAILED", Status.class, context));
        assertSame(Status.OK, NodeMapper.toEnum(new EncodedA("OK"), Status.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toEnum("missing", Status.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toEnum(1, Status.class, context));

        assertNull(NodeMapper.asEnum(null, Status.class, context));
        assertSame(Status.OK, NodeMapper.asEnum(Status.OK, Status.class, context));
        assertSame(Status.FAILED, NodeMapper.asEnum(1L, Status.class, context));
        assertSame(Status.FAILED, NodeMapper.asEnum(new NumericValue(1L), Status.class, context));
        assertSame(SingleLetter.O, NodeMapper.asEnum('O', SingleLetter.class, context));
        assertNull(NodeMapper.asEnum("1", Status.class, context));
        assertNull(NodeMapper.asEnum("missing", Status.class, context));
        assertNull(NodeMapper.asEnum(1.5, Status.class, context));
        assertNull(NodeMapper.asEnum(-1, Status.class, context));
        assertNull(NodeMapper.asEnum(2, Status.class, context));
        assertNull(NodeMapper.asEnum(new BigInteger("4294967297"), Status.class, context));

        assertNull(NodeMapper.toNumber(null, context));
        assertEquals(2L, NodeMapper.toNumber(2L, context));
        assertEquals(3L, NodeMapper.toNumber(new NumericValue(3L), context));
        assertThrows(NodeException.class, () -> NodeMapper.toNumber(new EncodedA("3"), context));
        assertThrows(NodeException.class, () -> NodeMapper.toNumber("3", context));

        assertNull(NodeMapper.asNumber(null, context));
        assertEquals(2L, NodeMapper.asNumber(2L, context));
        assertEquals(1, NodeMapper.asNumber(true, context));
        assertEquals(0, NodeMapper.asNumber(false, context));
        assertEquals(0, NodeMapper.asNumber(Status.OK, context));
        assertEquals(3, NodeMapper.asNumber("3", context));
        assertEquals(4, NodeMapper.asNumber('4', context));
        assertEquals(5, NodeMapper.asNumber(new EncodedA("5"), context));
        assertThrows(NodeException.class, () -> NodeMapper.asNumber(new Object(), context));
    }

    @Test
    void directBooleanAdaptationChecksSupportedTextAndIntegralNumbers() {
        RuntimeContext context = RuntimeContext.EMPTY;
        assertNull(NodeMapper.toBoolean(null, context));
        assertEquals(true, NodeMapper.toBoolean(true, context));
        assertThrows(NodeException.class, () -> NodeMapper.toBoolean("true", context));
        assertThrows(NodeException.class, () -> NodeMapper.toBoolean(new EncodedA("true"), context));

        assertNull(NodeMapper.asBoolean(null, context));
        assertEquals(true, NodeMapper.asBoolean(true, context));
        for (String value : Arrays.asList("TRUE", "yes", "On", "1")) {
            assertEquals(true, NodeMapper.asBoolean(value, context));
        }
        for (String value : Arrays.asList("FALSE", "No", "off", "0")) {
            assertEquals(false, NodeMapper.asBoolean(value, context));
        }
        assertEquals(true, NodeMapper.asBoolean(1, context));
        assertEquals(false, NodeMapper.asBoolean(0L, context));
        assertEquals(true, NodeMapper.asBoolean(new EncodedA("yes"), context));
        assertThrows(NodeException.class, () -> NodeMapper.asBoolean("maybe", context));
        assertThrows(NodeException.class, () -> NodeMapper.asBoolean(2, context));
        assertThrows(NodeException.class, () -> NodeMapper.asBoolean(new Object(), context));
    }

    @Test
    void directObjectAdaptationRetainsShallowValuesAndTypesEntriesStrictly() {
        RuntimeContext context = RuntimeContext.EMPTY;
        JsonObject object = JsonObject.of("name", "Ada", "age", 3);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Ada");
        map.put("age", 3);

        assertNull(NodeMapper.toJsonObject(null, context));
        assertSame(object, NodeMapper.toJsonObject(object, context));
        assertEquals(map, NodeMapper.toJsonObject(map, context).toMap());
        assertEquals("Ada", NodeMapper.toJsonObject(profile(), context).getNode("name"));
        assertThrows(NodeException.class, () -> NodeMapper.toJsonObject(1, context));

        assertNull(NodeMapper.toMap(null, context));
        assertSame(map, NodeMapper.toMap(map, context));
        assertEquals(map, NodeMapper.toMap(object, context));
        assertEquals("Ada", NodeMapper.toMap(profile(), context).get("name"));
        assertThrows(NodeException.class, () -> NodeMapper.toMap("text", context));

        assertNull(NodeMapper.toMap(null, String.class, context));
        assertSame(map, NodeMapper.toMap(map, null, context));
        assertSame(map, NodeMapper.toMap(map, Object.class, context));
        assertEquals(Map.of("first", "a", "second", "b"), NodeMapper.toMap(
                JsonObject.of("first", "a", "second", "b"), String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toMap(object, String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toMap(1, String.class, context));
    }

    @Test
    void directArrayAdaptationReusesShallowContainersAndChecksTypedElements() {
        RuntimeContext context = RuntimeContext.EMPTY;
        List<Object> list = new ArrayList<>(Arrays.asList("a", "b", "a"));
        JsonArray json = JsonArray.of("a", "b", "a");
        Set<String> set = new LinkedHashSet<>(Arrays.asList("a", "b"));
        String[] strings = {"a", "b"};

        assertNull(NodeMapper.toJsonArray(null, context));
        assertSame(json, NodeMapper.toJsonArray(json, context));
        assertEquals(list, NodeMapper.toJsonArray(list, context).toList());
        assertEquals(JsonArray.of("a", "b"), NodeMapper.toJsonArray(set, context));
        assertEquals(JsonArray.of(1, 2), NodeMapper.toJsonArray(new int[]{1, 2}, context));
        assertThrows(NodeException.class, () -> NodeMapper.toJsonArray(Map.of("name", "Ada"), context));

        assertNull(NodeMapper.toList(null, context));
        assertSame(list, NodeMapper.toList(list, context));
        assertEquals(list, NodeMapper.toList(json, context));
        assertEquals(Arrays.asList("a", "b"), NodeMapper.toList(set, context));
        assertEquals(Arrays.asList(1, 2), NodeMapper.toList(new int[]{1, 2}, context));
        assertThrows(NodeException.class, () -> NodeMapper.toList(Map.of("name", "Ada"), context));
        assertNull(NodeMapper.toList(null, String.class, context));
        assertSame(list, NodeMapper.toList(list, Object.class, context));
        assertEquals(Arrays.asList("a", "b"), NodeMapper.toList(strings, String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toList(json, Integer.class, context));

        assertNull(NodeMapper.toSet(null, context));
        assertSame(set, NodeMapper.toSet(set, context));
        assertEquals(set, NodeMapper.toSet(list, context));
        assertEquals(set, NodeMapper.toSet(json, context));
        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 2)),
                NodeMapper.toSet(new int[]{1, 2, 1}, context));
        assertThrows(NodeException.class, () -> NodeMapper.toSet(Map.of("name", "Ada"), context));
        assertNull(NodeMapper.toSet(null, String.class, context));
        assertSame(set, NodeMapper.toSet(set, Object.class, context));
        assertEquals(set, NodeMapper.toSet(strings, String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toSet(json, Integer.class, context));

        assertNull(NodeMapper.toArray(null, context));
        assertSame(strings, NodeMapper.toArray(strings, context));
        assertArrayEquals(new Object[]{1, 2}, NodeMapper.toArray(new int[]{1, 2}, context));
        assertArrayEquals(new Object[]{"a", "b", "a"}, NodeMapper.toArray(list, context));
        assertArrayEquals(new Object[]{"a", "b", "a"}, NodeMapper.toArray(json, context));
        assertArrayEquals(strings, NodeMapper.toArray(set, context));
        assertThrows(NodeException.class, () -> NodeMapper.toArray(Map.of("name", "Ada"), context));
        assertNull(NodeMapper.toArray(null, String.class, context));
        assertSame(strings, NodeMapper.toArray(strings, Object.class, context));
        assertArrayEquals(new String[]{"a", "b", "a"}, NodeMapper.toArray(json, String.class, context));
        assertThrows(IllegalArgumentException.class, () -> NodeMapper.toArray(list, int.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toArray(json, Integer.class, context));
    }

    @Test
    void directClassAdaptationRoutesScalarsArraysAndStructuralTargets() {
        RuntimeContext context = RuntimeContext.EMPTY;
        JsonArray json = JsonArray.of("a", "b");
        assertSame(json, NodeMapper.to(json, JsonArray.class, context));
        assertSame(json, NodeMapper.as(json, Object.class, context));
        assertThrows(NullPointerException.class, () -> NodeMapper.to(json, null, context));
        assertNull(NodeMapper.to(null, String.class, context));
        assertEquals("<null>", NodeMapper.as(null, NullValue.class, context).value);
        assertEquals("3", NodeMapper.as(3, String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.to(3, String.class, context));
        assertEquals(true, NodeMapper.as("yes", boolean.class, context));
        assertEquals(true, NodeMapper.to(true, Boolean.class, context));
        assertEquals(3, NodeMapper.to(3L, int.class, context));
        assertEquals(3, NodeMapper.as("3", Integer.class, context));
        assertEquals('a', NodeMapper.to("a", char.class, context));
        assertEquals('a', NodeMapper.as("abc", Character.class, context));
        assertSame(Status.OK, NodeMapper.to("OK", Status.class, context));
        assertSame(Status.FAILED, NodeMapper.as(1, Status.class, context));
        assertNull(NodeMapper.as("unknown", Status.class, context));

        assertEquals(Map.of("name", "Ada"), NodeMapper.to(Map.of("name", "Ada"), Map.class, context));
        assertEquals("Ada", NodeMapper.to(JsonObject.of("name", "Ada"), Map.class, context).get("name"));
        assertEquals("Ada", NodeMapper.to(Map.of("name", "Ada"), JsonObject.class, context).getNode("name"));
        assertEquals(Arrays.asList("a", "b"), NodeMapper.to(json, List.class, context));
        assertEquals(json, NodeMapper.to(Arrays.asList("a", "b"), JsonArray.class, context));
        assertEquals(new LinkedHashSet<>(Arrays.asList("a", "b")), NodeMapper.to(json, Set.class, context));
        assertArrayEquals(new String[]{"a", "b"}, NodeMapper.to(json, String[].class, context));
        assertArrayEquals(new int[]{1, 2}, NodeMapper.to(new long[]{1, 2}, int[].class, context));
        assertArrayEquals(new int[]{1, 2}, NodeMapper.as(Arrays.asList("1", "2"), int[].class, context));
        assertThrows(NodeException.class, () -> NodeMapper.to(Arrays.asList("1"), int[].class, context));
        assertEquals("Ada", NodeMapper.to(Map.of("name", "Ada"), NameOnly.class, context).name);
        assertEquals("Ada", NodeMapper.as(Map.of("name", "Ada"), NameOnly.class, context).name);
    }

    @Test
    void directTypedContainersHandleNullsAndRejectIncompatibleSources() {
        RuntimeContext context = RuntimeContext.EMPTY;
        Map<String, String> typedMap = NodeMapper.toMap(
                JsonObject.of("empty", null, "letter", 'a'), String.class, context);
        assertEquals("a", typedMap.get("letter"));
        assertNull(typedMap.get("empty"));
        assertEquals(Arrays.asList("a", null),
                NodeMapper.toList(JsonArray.of("a", null), String.class, context));
        assertEquals(new LinkedHashSet<>(Arrays.asList("a", null)),
                NodeMapper.toSet(Arrays.asList("a", null), String.class, context));
        assertArrayEquals(new String[]{"a", null},
                NodeMapper.toArray(JsonArray.of("a", null), String.class, context));
        assertEquals(Arrays.asList("a"), NodeMapper.toList(JsonArray.of("a"), null, context));
        assertEquals(Set.of("a"), NodeMapper.toSet(JsonArray.of("a"), null, context));
        assertArrayEquals(new Object[]{"a"}, NodeMapper.toArray(JsonArray.of("a"), null, context));

        assertThrows(NodeException.class, () -> NodeMapper.toList(Map.of("key", 1), String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toSet(Map.of("key", 1), String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toArray(Map.of("key", 1), String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.toMap(JsonArray.of(1), String.class, context));

        assertEquals("a", NodeMapper.to('a', String.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.to(1, Boolean.class, context));
        assertNull(NodeMapper.as(new NullValue(null), Integer.class, context));
        assertThrows(NodeException.class, () -> NodeMapper.to(new EncodedA("a"), int[].class, context));
        assertThrows(NodeException.class, () -> NodeMapper.to(Map.of("a", 1), int[].class, context));
    }

    @Test
    void directClassArraysRejectNodeValuesWithoutArrayShape() {
        RuntimeContext context = RuntimeContext.EMPTY;
        assertThrows(NodeException.class,
                () -> NodeMapper.to(new StringListValue(Arrays.asList("a", "b")), String[].class, context));
        assertThrows(NodeException.class,
                () -> NodeMapper.to(new StringListValue(null), String[].class, context));
        assertThrows(NodeException.class,
                () -> NodeMapper.to(new StringListValue(Arrays.asList("a", 1)), String[].class, context));
    }

    @NodeValue
    static class StringListValue {
        final List<?> values;

        StringListValue(List<?> values) {
            this.values = values;
        }

        @ValueToRaw List<?> encode() { return values; }
        @RawToValue static StringListValue decode(List<?> raw) { return new StringListValue(raw); }
    }

    enum SingleLetter { O }

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

    @OneOf(mappings = @OneOfMapping(type = SelectedObject.class, when = "child"), key = "kind")
    static class ObjectChoice extends JsonObject {}

    static class SelectedObject extends ObjectChoice {}

    @OneOf(mappings = @OneOfMapping(type = SelectedArray.class))
    static class ArrayChoice extends JsonArray {}

    static class SelectedArray extends ArrayChoice {}

    @OneOf(mappings = @OneOfMapping(type = SelectedChoice.class, when = "child"), key = "kind")
    static class Choice extends JsonObject {}

    static class SelectedChoice extends Choice {}

    static class OtherChoice extends Choice {}

    static class ChoiceHolder {
        @OneOf(mappings = @OneOfMapping(type = OtherChoice.class, when = "child"), key = "kind")
        public Choice value;
    }

    static class NullOverrideHolder {
        @OneOf(mappings = @OneOfMapping(type = NullValueSubtype.class, when = "child"), key = "kind")
        public NullValue value;
    }

    static class NullValueSubtype extends NullValue {
        NullValueSubtype(String value) { super(value); }
    }

    @OneOf(mappings = @OneOfMapping(type = EncodedChoice.class, when = "child"), key = "kind")
    static abstract class ValueChoice {}

    @NodeValue
    static class EncodedChoice extends ValueChoice {
        final Map<String, Object> raw;
        EncodedChoice(Map<String, Object> raw) { this.raw = raw; }
        @ValueToRaw Map<String, Object> encode() { return raw; }
        @RawToValue static EncodedChoice decode(Map<String, Object> raw) { return new EncodedChoice(raw); }
        @ValueCopy EncodedChoice copy() { return new EncodedChoice(raw); }
    }

    @NodeValue
    static class RawChoice {
        final Map<String, Object> raw;
        RawChoice(Map<String, Object> raw) { this.raw = raw; }
        @ValueToRaw Map<String, Object> encode() {
            if (raw != null && "error".equals(raw.get("kind"))) throw new IllegalStateException("bad raw");
            return raw;
        }
        @RawToValue static RawChoice decode(Map<String, Object> raw) { return new RawChoice(raw); }
    }

    @OneOf(mappings = @OneOfMapping(type = NullableValueSubtype.class, when = "child"),
            key = "kind", onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL)
    static abstract class NullableValueChoice extends ValueChoice {}

    static class NullableValueSubtype extends NullableValueChoice {}

    @OneOf(mappings = @OneOfMapping(type = StringChoice.class))
    static abstract class ShapeChoice {}

    @NodeValue
    static class StringChoice extends ShapeChoice {
        final String value;
        StringChoice(String value) { this.value = value; }
        @ValueToRaw String encode() { return value; }
        @RawToValue static StringChoice decode(String raw) { return new StringChoice(raw); }
        @ValueCopy StringChoice copy() { return new StringChoice(value); }
    }

}
