package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.NodeConfig;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfCase;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.NodeException;
import org.sjf4j.fixture.JsonObjectPersonFixture.Baby;
import org.sjf4j.fixture.JsonObjectPersonFixture.Person;

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
    void toArrayUsesListSnapshotWithoutIndexedReads() {
        List<Object> source = new LinkedList<Object>() {
            {
                add("a");
                add(null);
                add("c");
            }

            @Override
            public Object get(int index) {
                throw new AssertionError("toArray must not use indexed reads");
            }
        };

        Object[] snapshot = NodeMapper.toArray(source, NodeConfig.DEFAULT);
        assertArrayEquals(new Object[]{"a", null, "c"}, snapshot);
        source.set(0, "changed");
        assertEquals("a", snapshot[0]);
        assertNotSame(source, snapshot);
    }

    @Test
    void convertClassHandlesNullScalarsAndEnums() {
        assertNull(NodeMapper.convert(null, String.class, false, NodeConfig.DEFAULT));
        assertEquals(Integer.valueOf(12), NodeMapper.convert(12L, Integer.class, false, NodeConfig.DEFAULT));
        assertEquals(Character.valueOf('a'), NodeMapper.convert("a", Character.class, false, NodeConfig.DEFAULT));
        assertThrows(BindingException.class, () -> NodeMapper.convert("abc", Character.class, false, NodeConfig.DEFAULT));
        assertThrows(BindingException.class, () -> NodeMapper.convert("", Character.class, false, NodeConfig.DEFAULT));
        assertEquals("a", NodeMapper.convert('a', String.class, false, NodeConfig.DEFAULT));
        assertEquals(Boolean.TRUE, NodeMapper.convert(true, Boolean.class, false, NodeConfig.DEFAULT));
        assertEquals(Status.OK, NodeMapper.convert("OK", Status.class, false, NodeConfig.DEFAULT));
        assertEquals("OK", NodeMapper.convert(Status.OK, String.class, false, NodeConfig.DEFAULT));
        assertNull(NodeMapper.convert(null, void.class, false, NodeConfig.DEFAULT));
        assertThrows(BindingException.class, () -> NodeMapper.convert("x", Void.class, false, NodeConfig.DEFAULT));

        assertEquals(Integer.valueOf(7),
                NodeMapper.convert(7L, Integer.class, false, NodeConfig.DEFAULT));
    }

    @Test
    void convertTypeAndTypeReferenceHandleTopLevelGenerics() {
        TypeReference<List<User>> reference = new TypeReference<List<User>>() {};
        JsonArray node = JsonArray.of(
                JsonObject.of("name", "A", "age", 7),
                JsonObject.of("name", "B", "age", 8));

        List<User> fromReference = (List<User>) NodeMapper.convert(node, reference.getType(), false, NodeConfig.DEFAULT);
        List<User> fromReferenceWithContext =
                (List<User>) NodeMapper.convert(node, reference.getType(), false, NodeConfig.DEFAULT);
        Type type = reference.getType();
        List<User> fromType = (List<User>) NodeMapper.convert(node, type, false, NodeConfig.DEFAULT);
        List<User> fromTypeWithContext =
                (List<User>) NodeMapper.convert(node, type, false, NodeConfig.DEFAULT);

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
                new TypeReference<Envelope<List<User>>>() {}.getType(), false, NodeConfig.DEFAULT);

        assertEquals(200, result.code);
        assertInstanceOf(User.class, result.body.get(0));
        assertEquals("B", result.body.get(1).name);
    }

    @Test
    void convertHandlesPojoMapArraySetAndJsonNodeStructures() {
        Profile source = profile();

        Map<String, Object> map = (Map<String, Object>) NodeMapper.convert(source,
                new TypeReference<Map<String, Object>>() {}.getType(), false, NodeConfig.DEFAULT);
        JsonObject json = (JsonObject) NodeMapper.convert(map, JsonObject.class, false, NodeConfig.DEFAULT);
        Profile rebuilt = (Profile) NodeMapper.convert(json, Profile.class, false, NodeConfig.DEFAULT);
        String[] names = (String[]) NodeMapper.convert(JsonArray.of("A", "B"), String[].class, false, NodeConfig.DEFAULT);
        Set<Integer> values = (Set<Integer>) NodeMapper.convert(new int[]{1, 2, 2},
                new TypeReference<Set<Integer>>() {}.getType(), false, NodeConfig.DEFAULT);
        List<String> ordered = (List<String>) NodeMapper.convert(
                new LinkedHashSet<>(Arrays.asList("first", "second")),
                new TypeReference<List<String>>() {}.getType(), false, NodeConfig.DEFAULT);

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
                "ranks", JsonArray.of()), Profile.class, false, NodeConfig.DEFAULT);
        Profile nullable = (Profile) NodeMapper.convert(JsonObject.of(
                "name", null,
                "aliases", JsonArray.of("Ada", null),
                "details", null), Profile.class, false, NodeConfig.DEFAULT);

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
            NameOnly only = (NameOnly) NodeMapper.convert(source, NameOnly.class, false, NodeConfig.DEFAULT);
            assertEquals("Ada", only.name);

            CreatorTarget creator = (CreatorTarget) NodeMapper.convert(
                    source, CreatorTarget.class, false, NodeConfig.DEFAULT);
            assertEquals("Ada", creator.name);
            assertEquals(32, creator.age);

            OpenTarget open = (OpenTarget) NodeMapper.convert(source, OpenTarget.class, false, NodeConfig.DEFAULT);
            assertEquals("Ada", open.name);
            assertEquals(32, open.getNode("age"));
            assertEquals("extra", open.getNode("unused"));
            assertTrue(!open.dynamicMembers().containsKey("name"));
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
            CreatorTarget creator = (CreatorTarget) NodeMapper.convert(source, CreatorTarget.class, false, NodeConfig.DEFAULT);
            assertEquals("Ada", creator.name);
            assertEquals(32, creator.age);

            OpenTarget open = (OpenTarget) NodeMapper.convert(source, OpenTarget.class, false, NodeConfig.DEFAULT);
            assertEquals("Ada", open.name);
            assertEquals(32, open.getNode("age"));
            assertEquals("extra", open.getNode("unused"));
            assertTrue(!open.dynamicMembers().containsKey("name"));
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
            CountingTarget target = (CountingTarget) NodeMapper.convert(input, CountingTarget.class, false, NodeConfig.DEFAULT);
            assertEquals("Ada", target.name);
            assertEquals(32, target.age);
            assertEquals("extra", target.unused);
        }
        assertEquals(3, CountingTarget.created);

        // The same direct-creation path must support JOJO's static properties
        // and dynamically stored keys.
        OpenTarget jojo = (OpenTarget) NodeMapper.convert(map, OpenTarget.class, false, NodeConfig.DEFAULT);
        assertEquals("Ada", jojo.name);
        assertEquals(32, jojo.getNode("age"));
        assertEquals("extra", jojo.getNode("unused"));
    }

    @Test
    void valueCodecCopyAndRawBoundaries() {
        StringChoice value = new StringChoice("hello");
        assertSame(value, NodeMapper.convert(value, StringChoice.class, false, NodeConfig.DEFAULT));
        StringChoice copied = (StringChoice) NodeMapper.convert(value, StringChoice.class, true, NodeConfig.DEFAULT);
        assertNotSame(value, copied);
        assertEquals("hello", copied.value);

        assertEquals("hello", ((EncodedB) NodeMapper.convert(
                new EncodedA("hello"), EncodedB.class, true, NodeConfig.DEFAULT)).value);
        assertEquals("hello", NodeMapper.convertToRaw(value, NodeConfig.DEFAULT));
        assertEquals("<null>", ((NullValue) NodeMapper.convert(
                null, NullValue.class, true, NodeConfig.DEFAULT)).value);
        BindingException incompatible = assertThrows(BindingException.class, () ->
                NodeMapper.convert(new EncodedA("7"), NumericValue.class, false, NodeConfig.DEFAULT));
        assertTrue(incompatible.getMessage().contains("no ValueCodec"));
    }

    @Test
    void incompatibleStructuralTargetsAndNestedMembersReportPaths() {
        BindingException member = assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonObject.of("body", JsonArray.of(JsonObject.of("age", true))),
                        new TypeReference<Envelope<List<User>>>() {}.getType(), false, NodeConfig.DEFAULT));
        assertTrue(member.getMessage().contains("$.body[0].age"));

        BindingException mapValue = assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonObject.of("first", JsonArray.of(1, true)),
                        new TypeReference<Map<String, List<Integer>>>() {}.getType(), false, NodeConfig.DEFAULT));
        assertTrue(mapValue.getMessage().contains("$.first[1]"));

        assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonArray.of(1), Map.class, false, NodeConfig.DEFAULT));
        assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonObject.of("a", 1), List.class, false, NodeConfig.DEFAULT));
    }

    @Test
    void constructsRequestedContainersAndRejectsUnsupportedBuilders() {
        JsonObject object = JsonObject.of("b", 2, "a", 1);
        TreeMap<String, Integer> sorted = (TreeMap<String, Integer>) NodeMapper.convert(
                object, new TypeReference<TreeMap<String, Integer>>() {}.getType(), false, NodeConfig.DEFAULT);
        assertEquals(Arrays.asList("a", "b"), new ArrayList<>(sorted.keySet()));

        LinkedList<Long> linked = (LinkedList<Long>) NodeMapper.convert(
                JsonArray.of(2, 1), new TypeReference<LinkedList<Long>>() {}.getType(), false, NodeConfig.DEFAULT);
        assertEquals(Arrays.asList(2L, 1L), linked);

        TreeSet<Integer> set = (TreeSet<Integer>) NodeMapper.convert(
                JsonArray.of(2, 1, 2), new TypeReference<TreeSet<Integer>>() {}.getType(), false, NodeConfig.DEFAULT);
        assertEquals(Arrays.asList(1, 2), new ArrayList<>(set));

        BindingException nullValue = assertThrows(BindingException.class, () ->
                NodeMapper.convert(JsonObject.of("missing", null), ConcurrentHashMap.class, false, NodeConfig.DEFAULT));
        assertInstanceOf(NullPointerException.class, nullValue.getCause());
        assertThrows(BindingException.class, () -> NodeMapper.convert(
                JsonArray.of(1), java.util.SortedSet.class, false, NodeConfig.DEFAULT));
        assertThrows(BindingException.class, () -> NodeMapper.convert(
                Arrays.asList("a", "b"), Object.class, true, NodeConfig.DEFAULT));
    }

    @Test
    void convertsSetIntoEachIndexedRepresentationWithElementConversion() {
        Set<Integer> source = new LinkedHashSet<>(Arrays.asList(2, 1));
        Type longs = new TypeReference<List<Long>>() {}.getType();

        assertEquals(Arrays.asList(2L, 1L),
                NodeMapper.convert(source, longs, false, NodeConfig.DEFAULT));
        assertEquals(JsonArray.of(2, 1),
                NodeMapper.convert(source, JsonArray.class, false, NodeConfig.DEFAULT));
        assertArrayEquals(new long[]{2, 1},
                (long[]) NodeMapper.convert(source, long[].class, false, NodeConfig.DEFAULT));
        assertEquals(new LinkedHashSet<>(Arrays.asList(2L, 1L)),
                NodeMapper.convert(source, new TypeReference<Set<Long>>() {}.getType(), false, NodeConfig.DEFAULT));
        IntJajo jajo = (IntJajo) NodeMapper.convert(source, IntJajo.class, false, NodeConfig.DEFAULT);
        assertEquals(2, jajo.getNode(0));

        BindingException error = assertThrows(BindingException.class, () ->
                NodeMapper.convert(new LinkedHashSet<>(Arrays.asList(1, true)),
                        new TypeReference<Set<Integer>>() {}.getType(), false, NodeConfig.DEFAULT));
        assertTrue(error.getMessage().contains("$[1]"));
        assertThrows(BindingException.class, () ->
                NodeMapper.convert(source, Map.class, false, NodeConfig.DEFAULT));
    }

    @Test
    void rawConversionEncodesNestedValuesAndRejectsUnsupportedNodes() {
        JsonObject source = JsonObject.of("values", JsonArray.of(
                new EncodedA("a"), Status.OK, 'x', new int[]{1, 2}));

        Map<String, Object> raw = (Map<String, Object>) NodeMapper.convertToRaw(source, NodeConfig.DEFAULT);
        assertEquals(Arrays.asList("a", "OK", "x", Arrays.asList(1, 2)), raw.get("values"));
        assertNotSame(source.getNode("values"), raw.get("values"));
        assertEquals(Arrays.asList(2, 1), NodeMapper.convertToRaw(
                new LinkedHashSet<>(Arrays.asList(2, 1)), NodeConfig.DEFAULT));

        BindingException error = assertThrows(BindingException.class, () ->
                NodeMapper.convertToRaw(JsonArray.of(new Object()), NodeConfig.DEFAULT));
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

        Map<String, Object> raw = (Map<String, Object>) NodeMapper.convertToRaw(person, NodeConfig.DEFAULT);
        Map<String, Object> rawWithContext =
                (Map<String, Object>) NodeMapper.convertToRaw(person, NodeConfig.DEFAULT);

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
                        new TypeReference<List<Integer>>() {}.getType(), false, NodeConfig.DEFAULT));

        assertTrue(error.hasPathSegment());
        assertTrue(error.getMessage().contains("$[1]"));
    }

    @Test
    void convertDecodesNullWithNodeValue() {
        NullValue root = (NullValue) NodeMapper.convert(null, NullValue.class, false, NodeConfig.DEFAULT);
        NullValueHolder holder = (NullValueHolder) NodeMapper.convert(JsonObject.of("value", null), NullValueHolder.class, false, NodeConfig.DEFAULT);
        List<NullValue> list = (List<NullValue>) NodeMapper.convert(JsonArray.of((Object) null),
                new TypeReference<List<NullValue>>() {}.getType(), false, NodeConfig.DEFAULT);

        assertEquals("<null>", root.value);
        assertEquals("<null>", holder.value.value);
        assertEquals("<null>", list.get(0).value);
    }

    @Test
    void annotatedJsonSubclassesSelectOneOfBeforeStructuralOrIdentityConversion() {
        JsonObject object = JsonObject.of("kind", "child", "name", "A");
        JsonArray array = JsonArray.of(1, 2);

        assertInstanceOf(SelectedObject.class, NodeMapper.convert(object, ObjectChoice.class, false, NodeConfig.DEFAULT));
        assertInstanceOf(SelectedArray.class, NodeMapper.convert(array, ArrayChoice.class, false, NodeConfig.DEFAULT));
        SelectedObject compatibleObject = new SelectedObject();
        compatibleObject.put("kind", "child");
        assertSame(compatibleObject, NodeMapper.convert(compatibleObject, ObjectChoice.class, false, NodeConfig.DEFAULT));
        compatibleObject.put("kind", "unknown");
        assertThrows(BindingException.class,
                () -> NodeMapper.convert(compatibleObject, ObjectChoice.class, false, NodeConfig.DEFAULT));
        SelectedArray compatibleArray = new SelectedArray();
        assertSame(compatibleArray, NodeMapper.convert(compatibleArray, ArrayChoice.class, false, NodeConfig.DEFAULT));
        assertNull(NodeMapper.convert(null, ObjectChoice.class, false, NodeConfig.DEFAULT));
    }

    @Test
    void memberOneOfOverridesClassSelectionAndNullCodec() {
        ChoiceHolder holder = (ChoiceHolder) NodeMapper.convert(
                JsonObject.of("value", JsonObject.of("kind", "child")), ChoiceHolder.class, false, NodeConfig.DEFAULT);
        assertInstanceOf(OtherChoice.class, holder.value);

        ChoiceHolder empty = (ChoiceHolder) NodeMapper.convert(
                JsonObject.of("value", null), ChoiceHolder.class, false, NodeConfig.DEFAULT);
        assertNull(empty.value);

        NullOverrideHolder nullOverride = (NullOverrideHolder) NodeMapper.convert(
                JsonObject.of("value", null), NullOverrideHolder.class, false, NodeConfig.DEFAULT);
        assertNull(nullOverride.value);
    }

    @Test
    void oneOfTreatsUnknownSourcesAsNoMatchWithoutEncodingThem() {
        assertInstanceOf(EncodedChoice.class, NodeMapper.convert(
                Map.of("kind", "child"), ValueChoice.class, false, NodeConfig.DEFAULT));
        assertNull(NodeMapper.convert(Map.of("kind", "missing"), NullableValueChoice.class, false, NodeConfig.DEFAULT));

        EncodedChoice existing = new EncodedChoice(Map.of("kind", "child"));
        assertSame(existing, NodeMapper.convert(existing, ValueChoice.class, false, NodeConfig.DEFAULT));
        EncodedChoice copied = (EncodedChoice) NodeMapper.convert(existing, ValueChoice.class, true, NodeConfig.DEFAULT);
        assertNotSame(existing, copied);
        assertEquals(existing.raw, copied.raw);

        // Mapping an already-bound subtype does not invoke its encoder or
        // recheck its serialized discriminator.
        EncodedChoice throwingEncoder = new EncodedChoice(Map.of("kind", "error"));
        assertSame(throwingEncoder, NodeMapper.convert(
                throwingEncoder, ValueChoice.class, false, NodeConfig.DEFAULT));

        BindingException unmatched = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new RawChoice(Map.of("kind", "child")),
                        ValueChoice.class, false, NodeConfig.DEFAULT));
        assertTrue(unmatched.getMessage().contains("does not support ValueCodec type"));
        assertNull(NodeMapper.convert(new RawChoice(null), NullableValueChoice.class, false, NodeConfig.DEFAULT));
        BindingException unknown = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new Object(), ValueChoice.class, false, NodeConfig.DEFAULT));
        assertTrue(unknown.getMessage().contains("node must be a JSON object"));

        BindingException failure = assertThrows(BindingException.class,
                () -> NodeMapper.convert(Collections.singletonList(new RawChoice(Map.of("kind", "error"))),
                        new TypeReference<List<ValueChoice>>() {}.getType(), false, NodeConfig.DEFAULT));
        assertTrue(failure.getMessage().contains("$[0]"));
        assertTrue(failure.getMessage().contains("does not support ValueCodec type"));
    }

    @Test
    void oneOfAcceptsAlreadyMappedValueBindingWithoutRawConversion() {
        StringChoice decoded = (StringChoice) NodeMapper.convert("text", ShapeChoice.class, false, NodeConfig.DEFAULT);
        assertEquals("text", decoded.value);

        StringChoice existing = new StringChoice("text");
        assertSame(existing, NodeMapper.convert(existing, ShapeChoice.class, false, NodeConfig.DEFAULT));
        StringChoice copy = (StringChoice) NodeMapper.convert(existing, ShapeChoice.class, true, NodeConfig.DEFAULT);
        assertNotSame(existing, copy);
        assertEquals(existing.value, copy.value);

        // Being String-backed is not sufficient: the concrete type must
        // actually belong to the @OneOf mapping.
        BindingException unmatched = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new UnmappedStringChoice("text"),
                        ShapeChoice.class, false, NodeConfig.DEFAULT));
        assertTrue(unmatched.getMessage().contains("does not support ValueCodec type"));
    }

    @Test
    void convertsBetweenNodeValuesUsingCompatibleRawRepresentations() {
        EncodedA source = new EncodedA("hello");

        EncodedB converted = (EncodedB) NodeMapper.convert(source, EncodedB.class, false, NodeConfig.DEFAULT);
        assertEquals("hello", converted.value);

        // No implicit String -> Number coercion at a ValueCodec boundary.
        BindingException error = assertThrows(BindingException.class,
                () -> NodeMapper.convert(source, NumericValue.class, false, NodeConfig.DEFAULT));
        assertTrue(error.getMessage().contains("no ValueCodec"));

        assertSame(source, NodeMapper.convert(source, EncodedA.class, false, NodeConfig.DEFAULT));
    }

    @Test
    void convertsNodeValueMembersOfPojoAndMapWithoutDroppingRawEncoding() {
        SourceValueHolder source = new SourceValueHolder();
        source.code = new EncodedA("abc");

        TargetValueHolder fromPojo = (TargetValueHolder) NodeMapper.convert(
                source, TargetValueHolder.class, false, NodeConfig.DEFAULT);
        assertEquals("abc", fromPojo.code.value);

        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("code", new EncodedA("xyz"));
        TargetValueHolder fromMap = (TargetValueHolder) NodeMapper.convert(
                values, TargetValueHolder.class, false, NodeConfig.DEFAULT);
        assertEquals("xyz", fromMap.code.value);
    }

    @Test
    void directStringAndCharacterAdaptationDistinguishesStrictAndLenient() {
        NodeConfig config = NodeConfig.DEFAULT;
        assertNull(NodeMapper.toString(null, config));
        assertEquals("text", NodeMapper.toString("text", config));
        assertEquals("x", NodeMapper.toString('x', config));
        assertEquals("OK", NodeMapper.toString(Status.OK, config));
        assertEquals("value", NodeMapper.toString(new EncodedA("value"), config));
        assertThrows(NodeException.class, () -> NodeMapper.toString(new NumericValue(1L), config));
        assertThrows(NodeException.class, () -> NodeMapper.toString(42, config));

        assertNull(NodeMapper.asString(null, config));
        assertEquals("42", NodeMapper.asString(42, config));
        assertEquals("true", NodeMapper.asString(true, config));
        assertEquals("OK", NodeMapper.asString(Status.OK, config));
        assertEquals("7", NodeMapper.asString(new NumericValue(7L), config));

        assertNull(NodeMapper.toChar(null, config));
        assertEquals('x', NodeMapper.toChar('x', config));
        assertEquals('x', NodeMapper.toChar("x", config));
        assertEquals('x', NodeMapper.toChar(new EncodedA("x"), config));
        assertThrows(NodeException.class, () -> NodeMapper.toChar("", config));
        assertThrows(NodeException.class, () -> NodeMapper.toChar("xy", config));
        assertThrows(NodeException.class, () -> NodeMapper.toChar(1, config));

        assertNull(NodeMapper.asChar(null, config));
        assertNull(NodeMapper.asChar("", config));
        assertEquals('x', NodeMapper.asChar('x', config));
        assertEquals('x', NodeMapper.asChar("xyz", config));
        assertEquals('1', NodeMapper.asChar(123, config));
    }

    @Test
    void directEnumAndNumberAdaptationChecksNamesOrdinalsAndRawTypes() {
        NodeConfig config = NodeConfig.DEFAULT;
        assertNull(NodeMapper.toEnum(null, Status.class, config));
        assertSame(Status.OK, NodeMapper.toEnum(Status.OK, Status.class, config));
        assertSame(Status.FAILED, NodeMapper.toEnum("FAILED", Status.class, config));
        assertSame(Status.OK, NodeMapper.toEnum(new EncodedA("OK"), Status.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toEnum("missing", Status.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toEnum(1, Status.class, config));

        assertNull(NodeMapper.asEnum(null, Status.class, config));
        assertSame(Status.OK, NodeMapper.asEnum(Status.OK, Status.class, config));
        assertSame(Status.FAILED, NodeMapper.asEnum(1L, Status.class, config));
        assertSame(Status.FAILED, NodeMapper.asEnum(new NumericValue(1L), Status.class, config));
        assertSame(SingleLetter.O, NodeMapper.asEnum('O', SingleLetter.class, config));
        assertNull(NodeMapper.asEnum("1", Status.class, config));
        assertNull(NodeMapper.asEnum("missing", Status.class, config));
        assertNull(NodeMapper.asEnum(1.5, Status.class, config));
        assertNull(NodeMapper.asEnum(-1, Status.class, config));
        assertNull(NodeMapper.asEnum(2, Status.class, config));
        assertNull(NodeMapper.asEnum(new BigInteger("4294967297"), Status.class, config));

        assertNull(NodeMapper.toNumber(null, config));
        assertEquals(2L, NodeMapper.toNumber(2L, config));
        assertEquals(3L, NodeMapper.toNumber(new NumericValue(3L), config));
        assertThrows(NodeException.class, () -> NodeMapper.toNumber(new EncodedA("3"), config));
        assertThrows(NodeException.class, () -> NodeMapper.toNumber("3", config));

        assertNull(NodeMapper.asNumber(null, config));
        assertEquals(2L, NodeMapper.asNumber(2L, config));
        assertEquals(1, NodeMapper.asNumber(true, config));
        assertEquals(0, NodeMapper.asNumber(false, config));
        assertEquals(0, NodeMapper.asNumber(Status.OK, config));
        assertEquals(3, NodeMapper.asNumber("3", config));
        assertEquals(4, NodeMapper.asNumber('4', config));
        assertEquals(5, NodeMapper.asNumber(new EncodedA("5"), config));
        assertThrows(NodeException.class, () -> NodeMapper.asNumber(new Object(), config));
    }

    @Test
    void directBooleanAdaptationChecksSupportedTextAndIntegralNumbers() {
        NodeConfig config = NodeConfig.DEFAULT;
        assertNull(NodeMapper.toBoolean(null, config));
        assertEquals(true, NodeMapper.toBoolean(true, config));
        assertThrows(NodeException.class, () -> NodeMapper.toBoolean("true", config));
        assertThrows(NodeException.class, () -> NodeMapper.toBoolean(new EncodedA("true"), config));

        assertNull(NodeMapper.asBoolean(null, config));
        assertEquals(true, NodeMapper.asBoolean(true, config));
        for (String value : Arrays.asList("TRUE", "yes", "On", "1")) {
            assertEquals(true, NodeMapper.asBoolean(value, config));
        }
        for (String value : Arrays.asList("FALSE", "No", "off", "0")) {
            assertEquals(false, NodeMapper.asBoolean(value, config));
        }
        assertEquals(true, NodeMapper.asBoolean(1, config));
        assertEquals(false, NodeMapper.asBoolean(0L, config));
        assertEquals(true, NodeMapper.asBoolean(new EncodedA("yes"), config));
        assertThrows(NodeException.class, () -> NodeMapper.asBoolean("maybe", config));
        assertThrows(NodeException.class, () -> NodeMapper.asBoolean(2, config));
        assertThrows(NodeException.class, () -> NodeMapper.asBoolean(new Object(), config));
    }

    @Test
    void directObjectAdaptationRetainsShallowValuesAndTypesEntriesStrictly() {
        NodeConfig config = NodeConfig.DEFAULT;
        JsonObject object = JsonObject.of("name", "Ada", "age", 3);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Ada");
        map.put("age", 3);

        assertNull(NodeMapper.toJsonObject(null, config));
        assertSame(object, NodeMapper.toJsonObject(object, config));
        assertEquals(map, NodeMapper.toJsonObject(map, config).toMap());
        assertEquals("Ada", NodeMapper.toJsonObject(profile(), config).getNode("name"));
        assertThrows(NodeException.class, () -> NodeMapper.toJsonObject(1, config));

        assertNull(NodeMapper.toMap(null, config));
        assertSame(map, NodeMapper.toMap(map, config));
        assertEquals(map, NodeMapper.toMap(object, config));
        assertEquals("Ada", NodeMapper.toMap(profile(), config).get("name"));
        assertThrows(NodeException.class, () -> NodeMapper.toMap("text", config));

        assertNull(NodeMapper.toMap(null, String.class, config));
        assertSame(map, NodeMapper.toMap(map, null, config));
        assertSame(map, NodeMapper.toMap(map, Object.class, config));
        assertEquals(Map.of("first", "a", "second", "b"), NodeMapper.toMap(
                JsonObject.of("first", "a", "second", "b"), String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toMap(object, String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toMap(1, String.class, config));
    }

    @Test
    void directArrayAdaptationReusesShallowContainersAndChecksTypedElements() {
        NodeConfig config = NodeConfig.DEFAULT;
        List<Object> list = new ArrayList<>(Arrays.asList("a", "b", "a"));
        JsonArray json = JsonArray.of("a", "b", "a");
        Set<String> set = new LinkedHashSet<>(Arrays.asList("a", "b"));
        String[] strings = {"a", "b"};

        assertNull(NodeMapper.toJsonArray(null, config));
        assertSame(json, NodeMapper.toJsonArray(json, config));
        assertEquals(list, NodeMapper.toJsonArray(list, config).toList());
        assertEquals(JsonArray.of("a", "b"), NodeMapper.toJsonArray(set, config));
        assertEquals(JsonArray.of(1, 2), NodeMapper.toJsonArray(new int[]{1, 2}, config));
        assertThrows(NodeException.class, () -> NodeMapper.toJsonArray(Map.of("name", "Ada"), config));

        assertNull(NodeMapper.toList(null, config));
        assertSame(list, NodeMapper.toList(list, config));
        assertEquals(list, NodeMapper.toList(json, config));
        assertEquals(Arrays.asList("a", "b"), NodeMapper.toList(set, config));
        assertEquals(Arrays.asList(1, 2), NodeMapper.toList(new int[]{1, 2}, config));
        assertThrows(NodeException.class, () -> NodeMapper.toList(Map.of("name", "Ada"), config));
        assertNull(NodeMapper.toList(null, String.class, config));
        assertSame(list, NodeMapper.toList(list, Object.class, config));
        assertEquals(Arrays.asList("a", "b"), NodeMapper.toList(strings, String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toList(json, Integer.class, config));

        assertNull(NodeMapper.toSet(null, config));
        assertSame(set, NodeMapper.toSet(set, config));
        assertEquals(set, NodeMapper.toSet(list, config));
        assertEquals(set, NodeMapper.toSet(json, config));
        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 2)),
                NodeMapper.toSet(new int[]{1, 2, 1}, config));
        assertThrows(NodeException.class, () -> NodeMapper.toSet(Map.of("name", "Ada"), config));
        assertNull(NodeMapper.toSet(null, String.class, config));
        assertSame(set, NodeMapper.toSet(set, Object.class, config));
        assertEquals(set, NodeMapper.toSet(strings, String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toSet(json, Integer.class, config));

        assertNull(NodeMapper.toArray(null, config));
        assertSame(strings, NodeMapper.toArray(strings, config));
        assertArrayEquals(new Object[]{1, 2}, NodeMapper.toArray(new int[]{1, 2}, config));
        assertArrayEquals(new Object[]{"a", "b", "a"}, NodeMapper.toArray(list, config));
        assertArrayEquals(new Object[]{"a", "b", "a"}, NodeMapper.toArray(json, config));
        assertArrayEquals(strings, NodeMapper.toArray(set, config));
        assertThrows(NodeException.class, () -> NodeMapper.toArray(Map.of("name", "Ada"), config));
        assertNull(NodeMapper.toArray(null, String.class, config));
        assertSame(strings, NodeMapper.toArray(strings, Object.class, config));
        assertArrayEquals(new String[]{"a", "b", "a"}, NodeMapper.toArray(json, String.class, config));
        assertThrows(IllegalArgumentException.class, () -> NodeMapper.toArray(list, int.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toArray(json, Integer.class, config));
    }

    @Test
    void directClassAdaptationRoutesScalarsArraysAndStructuralTargets() {
        NodeConfig config = NodeConfig.DEFAULT;
        JsonArray json = JsonArray.of("a", "b");
        assertSame(json, NodeMapper.to(json, JsonArray.class, config));
        assertSame(json, NodeMapper.as(json, Object.class, config));
        assertThrows(NullPointerException.class, () -> NodeMapper.to(json, null, config));
        assertNull(NodeMapper.to(null, String.class, config));
        assertEquals("<null>", NodeMapper.as(null, NullValue.class, config).value);
        assertEquals("3", NodeMapper.as(3, String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.to(3, String.class, config));
        assertEquals(true, NodeMapper.as("yes", boolean.class, config));
        assertEquals(true, NodeMapper.to(true, Boolean.class, config));
        assertEquals(3, NodeMapper.to(3L, int.class, config));
        assertEquals(3, NodeMapper.as("3", Integer.class, config));
        assertEquals('a', NodeMapper.to("a", char.class, config));
        assertEquals('a', NodeMapper.as("abc", Character.class, config));
        assertSame(Status.OK, NodeMapper.to("OK", Status.class, config));
        assertSame(Status.FAILED, NodeMapper.as(1, Status.class, config));
        assertNull(NodeMapper.as("unknown", Status.class, config));

        assertEquals(Map.of("name", "Ada"), NodeMapper.to(Map.of("name", "Ada"), Map.class, config));
        assertEquals("Ada", NodeMapper.to(JsonObject.of("name", "Ada"), Map.class, config).get("name"));
        assertEquals("Ada", NodeMapper.to(Map.of("name", "Ada"), JsonObject.class, config).getNode("name"));
        assertEquals(Arrays.asList("a", "b"), NodeMapper.to(json, List.class, config));
        assertEquals(json, NodeMapper.to(Arrays.asList("a", "b"), JsonArray.class, config));
        assertEquals(new LinkedHashSet<>(Arrays.asList("a", "b")), NodeMapper.to(json, Set.class, config));
        assertArrayEquals(new String[]{"a", "b"}, NodeMapper.to(json, String[].class, config));
        assertArrayEquals(new int[]{1, 2}, NodeMapper.to(new long[]{1, 2}, int[].class, config));
        assertArrayEquals(new int[]{1, 2}, NodeMapper.as(Arrays.asList("1", "2"), int[].class, config));
        assertThrows(NodeException.class, () -> NodeMapper.to(Arrays.asList("1"), int[].class, config));
        assertEquals("Ada", NodeMapper.to(Map.of("name", "Ada"), NameOnly.class, config).name);
        assertEquals("Ada", NodeMapper.as(Map.of("name", "Ada"), NameOnly.class, config).name);
    }

    @Test
    void directTypedContainersHandleNullsAndRejectIncompatibleSources() {
        NodeConfig config = NodeConfig.DEFAULT;
        Map<String, String> typedMap = NodeMapper.toMap(
                JsonObject.of("empty", null, "letter", 'a'), String.class, config);
        assertEquals("a", typedMap.get("letter"));
        assertNull(typedMap.get("empty"));
        assertEquals(Arrays.asList("a", null),
                NodeMapper.toList(JsonArray.of("a", null), String.class, config));
        assertEquals(new LinkedHashSet<>(Arrays.asList("a", null)),
                NodeMapper.toSet(Arrays.asList("a", null), String.class, config));
        assertArrayEquals(new String[]{"a", null},
                NodeMapper.toArray(JsonArray.of("a", null), String.class, config));
        assertEquals(Arrays.asList("a"), NodeMapper.toList(JsonArray.of("a"), null, config));
        assertEquals(Set.of("a"), NodeMapper.toSet(JsonArray.of("a"), null, config));
        assertArrayEquals(new Object[]{"a"}, NodeMapper.toArray(JsonArray.of("a"), null, config));

        assertThrows(NodeException.class, () -> NodeMapper.toList(Map.of("key", 1), String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toSet(Map.of("key", 1), String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toArray(Map.of("key", 1), String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.toMap(JsonArray.of(1), String.class, config));

        assertEquals("a", NodeMapper.to('a', String.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.to(1, Boolean.class, config));
        assertNull(NodeMapper.as(new NullValue(null), Integer.class, config));
        assertThrows(NodeException.class, () -> NodeMapper.to(new EncodedA("a"), int[].class, config));
        assertThrows(NodeException.class, () -> NodeMapper.to(Map.of("a", 1), int[].class, config));
    }

    @Test
    void directClassArraysRejectNodeValuesWithoutArrayShape() {
        NodeConfig config = NodeConfig.DEFAULT;
        assertThrows(NodeException.class,
                () -> NodeMapper.to(new StringListValue(Arrays.asList("a", "b")), String[].class, config));
        assertThrows(NodeException.class,
                () -> NodeMapper.to(new StringListValue(null), String[].class, config));
        assertThrows(NodeException.class,
                () -> NodeMapper.to(new StringListValue(Arrays.asList("a", 1)), String[].class, config));
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

    @OneOf(cases = @OneOfCase(type = SelectedObject.class, when = "child"), discriminatorKey = "kind")
    static class ObjectChoice extends JsonObject {}

    static class SelectedObject extends ObjectChoice {}

    @OneOf(cases = @OneOfCase(type = SelectedArray.class))
    static class ArrayChoice extends JsonArray {}

    static class SelectedArray extends ArrayChoice {}

    @OneOf(cases = @OneOfCase(type = SelectedChoice.class, when = "child"), discriminatorKey = "kind")
    static class Choice extends JsonObject {}

    static class SelectedChoice extends Choice {}

    static class OtherChoice extends Choice {}

    static class ChoiceHolder {
        @OneOf(cases = @OneOfCase(type = OtherChoice.class, when = "child"), discriminatorKey = "kind")
        public Choice value;
    }

    static class NullOverrideHolder {
        @OneOf(cases = @OneOfCase(type = NullValueSubtype.class, when = "child"), discriminatorKey = "kind")
        public NullValue value;
    }

    static class NullValueSubtype extends NullValue {
        NullValueSubtype(String value) { super(value); }
    }

    @OneOf(cases = @OneOfCase(type = EncodedChoice.class, when = "child"), discriminatorKey = "kind")
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

    @OneOf(cases = @OneOfCase(type = NullableValueSubtype.class, when = "child"),
            discriminatorKey = "kind", onNoMatch = OneOf.OnNoMatch.RETURN_NULL)
    static abstract class NullableValueChoice extends ValueChoice {}

    static class NullableValueSubtype extends NullableValueChoice {}

    @OneOf(cases = @OneOfCase(type = StringChoice.class))
    static abstract class ShapeChoice {}

    @NodeValue
    static class StringChoice extends ShapeChoice {
        final String value;
        StringChoice(String value) { this.value = value; }
        @ValueToRaw String encode() { return value; }
        @RawToValue static StringChoice decode(String raw) { return new StringChoice(raw); }
        @ValueCopy StringChoice copy() { return new StringChoice(value); }
    }

    @NodeValue
    static class UnmappedStringChoice extends ShapeChoice {
        final String value;
        UnmappedStringChoice(String value) { this.value = value; }
        @ValueToRaw String encode() { return value; }
        @RawToValue static UnmappedStringChoice decode(String raw) {
            return new UnmappedStringChoice(raw);
        }
    }

}
