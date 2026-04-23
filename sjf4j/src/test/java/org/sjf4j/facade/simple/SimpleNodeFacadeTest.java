package org.sjf4j.facade.simple;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.NodeConverter;
import org.sjf4j.facade.NodeFacade;
import org.sjf4j.node.Nodes;
import org.sjf4j.node.ValueFormatMapping;
import org.sjf4j.patch.JsonPatch;
import org.sjf4j.patch.PatchOperation;
import org.sjf4j.path.JsonPointer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
@Slf4j
public class SimpleNodeFacadeTest {

    private NodeFacade nodeFacade = new SimpleNodeFacade();

    @Test
    void objectClassPreservesShape() {
        Object[] samples = {
                1,
                "x",
                Arrays.asList(1,2),
                Collections.singletonMap("a",1),
                JsonObject.of("x", 1),
                new Student()
        };

        Object r0 = nodeFacade.readNode(samples[0], Object.class);
        assertEquals(Integer.class, r0.getClass());

        Object r1 = nodeFacade.readNode(samples[1], Object.class);
        assertEquals(String.class, r1.getClass());

        Object r2 = nodeFacade.readNode(samples[2], Object.class);
        assertInstanceOf(List.class, r2);

        Object r3 = nodeFacade.readNode(samples[3], Object.class);
        assertInstanceOf(Map.class, r3);

        Object r4 = nodeFacade.readNode(samples[4], Object.class);
        assertInstanceOf(JsonObject.class, r4);

        Object r5 = nodeFacade.readNode(samples[5], Object.class);
        assertInstanceOf(Student.class, r5);
    }

    @Test
    void objectClassDeepCopySwitchWorks() {
        JsonObject src = JsonObject.of("name", "A", "meta", JsonObject.of("age", 18));

        Object same = nodeFacade.readNode(src, Object.class, false);
        assertTrue(same == src);

        JsonObject copied = (JsonObject) nodeFacade.readNode(src, Object.class, true);
        assertNotSame(src, copied);
        assertNotSame(src.getJsonObject("meta"), copied.getJsonObject("meta"));

        copied.getJsonObject("meta").put("age", 20);
        assertEquals(18, src.getJsonObject("meta").getInt("age"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Role {
        private String name;
        private float percentage;
    }

    @Data
    public static class User {
        private String name;
        private int age;
        private JsonObject info;
        private List<User> friends;
        private Map<String, Role> roles;
    }

    @Test
    public void testObject2Value1() {
        User baby = new User();
        baby.setName("Baby");
        baby.setAge(1);
        baby.setInfo(JsonObject.of("cc", "dd"));

        User lily = new User();
        lily.setName("Lily");
        lily.setAge(25);
        lily.setInfo(JsonObject.of("aa", "bb"));
        lily.setFriends(Collections.singletonList(baby));
        lily.setRoles(Collections.singletonMap("kk", new Role("Mom", 90.0f)));

        // object2Value
        Object value = nodeFacade.readNode(lily, JsonObject.class);
        log.info("value type={}, value={}", value.getClass(), value);
        assertEquals(JsonObject.class, value.getClass());
        assertEquals(25, ((JsonObject) value).getInt("age"));
        assertEquals("Baby",
                ((JsonObject) value).getJsonArray("friends").getJsonObject(0).getString("name"));
        assertEquals(90f, ((JsonObject) value).getFloatByPath("$.roles.kk.percentage"));

        // value2Object
        Object object = nodeFacade.readNode(value, User.class);
        log.info("object type={}, object={}", object.getClass(), object);
    }

    @Test
    void testReadSamePojoWithDeepCopyTrue() {
        User baby = new User();
        baby.setName("Baby");
        baby.setAge(1);
        baby.setInfo(JsonObject.of("k", "v"));

        User lily = new User();
        lily.setName("Lily");
        lily.setAge(25);
        lily.setInfo(JsonObject.of("city", "NY"));
        lily.setFriends(Collections.singletonList(baby));

        User copied = (User) nodeFacade.readNode(lily, User.class, true);
        assertNotSame(lily, copied);
        assertNotSame(lily.getInfo(), copied.getInfo());
        assertNotSame(lily.getFriends(), copied.getFriends());

        copied.getInfo().put("city", "BJ");
        copied.getFriends().get(0).setName("Kid");

        assertEquals("NY", lily.getInfo().getString("city"));
        assertEquals("Baby", lily.getFriends().get(0).getName());
    }

    @Test
    public void testValue2Object2() {
        Object o1 = nodeFacade.readNode(JsonObject.of("percentage", 0), Role.class);
        log.info("o1 type={}, o1={}", o1.getClass(), o1);
        assertEquals(Role.class, o1.getClass());
    }

    @Test
    public void testValue2Object3() {
        Object o1 = nodeFacade.readNode(5.55, long.class);
        log.info("o1 type={}, o1={}", o1.getClass(), o1);
    }


    /// AI generated

    // ========== Primitive/basic types ==========
    public static class BasicTypes {
        public String name;
        public int age;
        public Boolean vip;
        public Double score;
    }

    @Test
    public void testBasicTypes() {
        JsonObject jo = new JsonObject();
        jo.put("name", "Alice");
        jo.put("age", 18);
        jo.put("vip", true);
        jo.put("score", 99.5);

        BasicTypes pojo = (BasicTypes) nodeFacade.readNode(jo, BasicTypes.class);
        assertEquals("Alice", pojo.name);
        assertEquals(18, pojo.age);
        assertTrue(pojo.vip);
        assertEquals(99.5, pojo.score);

        JsonObject back = (JsonObject) nodeFacade.readNode(pojo, JsonObject.class);
        assertEquals("Alice", back.get("name"));
    }

    // ========== Nested object ==========
    public static class Address {
        public String city;
        public String street;
    }

    public static class Person {
        public String name;
        public Address address;
    }

    @Test
    public void testNestedObject() {
        JsonObject jo = new JsonObject();
        JsonObject addr = new JsonObject();
        addr.put("city", "New York");
        addr.put("street", "5th Ave");
        jo.put("name", "Bob");
        jo.put("address", addr);

        Person p = (Person) nodeFacade.readNode(jo, Person.class);
        assertEquals("Bob", p.name);
        assertEquals("New York", p.address.city);
        assertEquals("5th Ave", p.address.street);

        Map<String, Object> back = (Map<String, Object>) nodeFacade.writeNode(p);
        assertEquals("Bob", back.get("name"));
    }

    // ========== List and array ==========
    public static class Team {
        public String teamName;
        public List<String> members;
        public int[] scores;
    }

    @Test
    public void testListAndArray() {
        JsonObject jo = new JsonObject();
        jo.put("teamName", "Rangers");
        jo.put("members", Arrays.asList("Tom", "Jerry"));
        jo.put("scores", new int[]{10, 20, 30});

        Team t = (Team) nodeFacade.readNode(jo, Team.class);
        assertEquals("Rangers", t.teamName);
        assertEquals(Arrays.asList("Tom", "Jerry"), t.members);
        assertArrayEquals(new int[]{10, 20, 30}, t.scores);

        Map<String, Object> back = (Map<String, Object>) nodeFacade.writeNode(t);
        assertEquals("Rangers", back.get("teamName"));
    }

    // ========== List<POJO> ==========
    public static class Student {
        public String name;
        public int age;
    }

    public static class ClassRoom {
        public List<Student> students;
    }

    @Data
    @AnyOf(value = {
            @AnyOf.Mapping(value = AnyOfCat.class, when = "cat"),
            @AnyOf.Mapping(value = AnyOfDog.class, when = "dog")
    }, key = "kind")
    public static class AnyOfAnimal {
        private String kind;
        private String name;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class AnyOfCat extends AnyOfAnimal {
        private int lives;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class AnyOfDog extends AnyOfAnimal {
        private int bark;
    }

    public static class AnyOfZoo {
        @AnyOf(value = {
                @AnyOf.Mapping(value = AnyOfCat.class, when = "cat"),
                @AnyOf.Mapping(value = AnyOfDog.class, when = "dog")
        }, key = "kind")
        public AnyOfAnimal pet;
    }

    @AnyOf(value = {
            @AnyOf.Mapping(AnyOfPolyObj.class),
            @AnyOf.Mapping(AnyOfPolyArr.class)
    })
    interface AnyOfPoly {}

    static class AnyOfPolyObj extends JsonObject implements AnyOfPoly {}
    static class AnyOfPolyArr extends JsonArray implements AnyOfPoly {}

    @Test
    void testAnyOfRootByDiscriminator() {
        JsonObject jo = JsonObject.of("kind", "cat", "name", "Nana", "lives", 7);

        AnyOfAnimal animal = (AnyOfAnimal) nodeFacade.readNode(jo, AnyOfAnimal.class);
        assertInstanceOf(AnyOfCat.class, animal);
        assertEquals("Nana", animal.getName());
        assertEquals(7, ((AnyOfCat) animal).getLives());
    }

    @Test
    void testAnyOfFieldByDiscriminator() {
        JsonObject jo = JsonObject.of("pet", JsonObject.of("kind", "dog", "name", "Bobo", "bark", 3));
        AnyOfZoo zoo = (AnyOfZoo) nodeFacade.readNode(jo, AnyOfZoo.class);

        assertInstanceOf(AnyOfDog.class, zoo.pet);
        assertEquals("Bobo", zoo.pet.getName());
        assertEquals(3, ((AnyOfDog) zoo.pet).getBark());
    }

    @Test
    void testAnyOfRootByJsonType() {
        AnyOfPoly p1 = (AnyOfPoly) nodeFacade.readNode(JsonObject.of("k", 1), AnyOfPoly.class);
        AnyOfPoly p2 = (AnyOfPoly) nodeFacade.readNode(JsonArray.of(1, 2), AnyOfPoly.class);

        assertInstanceOf(AnyOfPolyObj.class, p1);
        assertInstanceOf(AnyOfPolyArr.class, p2);
    }

    @Test
    public void testListOfPojo() {
        JsonObject jo = new JsonObject();
        List<JsonObject> list = new ArrayList<>();
        list.add(JsonObject.of("name", "Ann", "age", 10));
        list.add(JsonObject.of("name", "Ben", "age", 12));
        jo.put("students", list);

        ClassRoom c = (ClassRoom) nodeFacade.readNode(jo, ClassRoom.class, true);
        log.info("c={}", Nodes.inspect(c));
        assertEquals(2, c.students.size());
        assertEquals("Ann", c.students.get(0).name);

        JsonObject back = (JsonObject) nodeFacade.readNode(c, JsonObject.class);
        log.info("back={}", back);
        Assertions.assertNotNull(back);
        Object students = back.get("students");
        log.info("students type={}, json={}", students.getClass(), students);
        assertTrue(students instanceof List);
    }

    // ========== Set<POJO> ==========
    public static class ClassRoomSet {
        public Set<Student> students;
    }

    @Test
    public void testSetOfPojo() {
        JsonObject jo = new JsonObject();
        List<JsonObject> list = new ArrayList<>();
        list.add(JsonObject.of("name", "Ann", "age", 10));
        list.add(JsonObject.of("name", "Ben", "age", 12));
        jo.put("students", list);

        ClassRoomSet c = (ClassRoomSet) nodeFacade.readNode(jo, ClassRoomSet.class);
        assertEquals(2, c.students.size());

        JsonObject back = (JsonObject) nodeFacade.readNode(c, JsonObject.class);
        Object students = back.get("students");
        log.info("students type={}, json={}", students.getClass(), Nodes.inspect(students));
        assertTrue(students instanceof Set);
    }

    @Test
    public void testSetToSet() {
        Set<String> input = new LinkedHashSet<>(Arrays.asList("a", "b"));
        Set<String> output = (Set<String>) nodeFacade.readNode(input, Set.class);
        assertEquals(input, output);
    }

    // ========== Map ==========
    public static class DictHolder {
        public Map<String, Integer> map;
        public Map<String, Student> nested;
    }

    @Test
    public void testMap() {
        JsonObject jo = new JsonObject();
        JsonObject map = new JsonObject();
        map.put("a", 1);
        map.put("b", 2);

        JsonObject nested = new JsonObject();
        nested.put("s1", JsonObject.of("name", "Alice", "age", 7));
        nested.put("s2", JsonObject.of("name", "Bob", "age", 8));

        jo.put("map", map);
        jo.put("nested", nested);

        log.info("jo={}", jo);
        DictHolder holder = (DictHolder) nodeFacade.readNode(jo, DictHolder.class);
        log.info("holder={} map={}", holder, holder.map);
        assertEquals(1, holder.map.get("a"));
//        assertEquals("Alice", holder.nested.get("s1").name);

        JsonObject back = (JsonObject) nodeFacade.readNode(holder, JsonObject.class);
        assertEquals(2, (back.getJsonObject("map")).size());
    }

    // ========== Enum ==========
    public enum Status { OK, FAIL }

    public static class Msg {
        public Status status;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEnum() {
        JsonObject jo = new JsonObject();
        jo.put("status", "OK");

        Msg msg = (Msg) nodeFacade.readNode(jo, Msg.class);
        assertEquals(Status.OK, msg.status);

        Msg back = (Msg) nodeFacade.readNode(msg, null);
        assertEquals(Status.OK, back.status);
    }

    // ========== Boolean Getter ==========
    public static class Flag {
        public boolean active;
    }

    @Test
    public void testBooleanField() {
        JsonObject jo = new JsonObject();
        jo.put("active", true);
        Flag f = (Flag) nodeFacade.readNode(jo, Flag.class);
        assertTrue(f.active);
    }


    @Test
    public void testNullFields() {
        JsonObject jo = new JsonObject();
        jo.put("name", null);
        BasicTypes bt = (BasicTypes) nodeFacade.readNode(jo, BasicTypes.class);
        assertNull(bt.name);
        assertEquals(0, bt.age); // default int=0

        jo.put("age", null);
        assertThrows(JsonException.class, () -> nodeFacade.readNode(jo, BasicTypes.class));
    }


    @Test
    public void testPrimitive1() {
        assertEquals(123, nodeFacade.readNode(123, int.class));
        assertEquals(123d, nodeFacade.readNode(123L, double.class));
        assertEquals('a', nodeFacade.readNode("abc", char.class));
        assertEquals('a', nodeFacade.readNode('a', char.class));
        assertEquals("a", nodeFacade.readNode('a', String.class));
        assertEquals(false, nodeFacade.readNode(false, boolean.class));
    }

    @Test
    public void testObjectArrayPreserveType() {
        int[] ints = new int[]{1, 2, 3};
        Object outInts = nodeFacade.readNode(ints, Object.class);
        assertTrue(outInts.getClass().isArray());
        assertEquals(int.class, outInts.getClass().getComponentType());
        assertArrayEquals(ints, (int[]) outInts);

        Integer[] boxed = new Integer[]{1, 2, 3};
        Object outBoxed = nodeFacade.readNode(boxed, Object.class);
        assertTrue(outBoxed instanceof Integer[]);
        assertArrayEquals(boxed, (Integer[]) outBoxed);
    }

    @Test
    public void testObjectJsonArrayPreserveType() {
        JsonArray input = JsonArray.of(1, "a", true);
        Object out = nodeFacade.readNode(input, Object.class);
        assertEquals(JsonArray.class, out.getClass());
        JsonArray outJa = (JsonArray) out;
        assertEquals(3, outJa.size());
        assertEquals(1, outJa.getNode(0));
        assertEquals("a", outJa.getNode(1));
        assertEquals(true, outJa.getNode(2));
    }

    @Test
    public void testObjectJsonArraySubclassPreserveType() {
        JsonPatch patch = new JsonPatch();
        patch.add(new PatchOperation("add", JsonPointer.compile("/a"), 1, null));

        Object out = nodeFacade.readNode(patch, Object.class);
        assertEquals(JsonPatch.class, out.getClass());
        JsonPatch outPatch = (JsonPatch) out;
        assertEquals(1, outPatch.size());
        PatchOperation operation = (PatchOperation) outPatch.getNode(0);
        assertEquals("add", operation.getOp());
    }

    @Test
    public void testObject1() {
        Map<String, Object> m = Collections.singletonMap("a", 1);
        Object o = nodeFacade.readNode(m, Object.class);
        assertInstanceOf(Map.class, o);
    }

    @Test
    public void testReadConcreteMapTargets() {
        JsonObject jo = JsonObject.of("b", 2, "a", 1);

        Map<?, ?> hashMap = (Map<?, ?>) nodeFacade.readNode(jo, HashMap.class);
        assertInstanceOf(HashMap.class, hashMap);
        assertEquals(2, hashMap.get("b"));

        Map<?, ?> concurrentMap = (Map<?, ?>) nodeFacade.readNode(jo, ConcurrentHashMap.class);
        assertInstanceOf(ConcurrentHashMap.class, concurrentMap);
        assertEquals(1, concurrentMap.get("a"));

        assertThrows(JsonException.class, () -> nodeFacade.readNode(jo, SortedMap.class));
    }

    @Test
    public void testReadConcreteListAndSetTargets() {
        JsonArray ja = JsonArray.of(2, 1, 3);

        List<?> linkedList = (List<?>) nodeFacade.readNode(ja, LinkedList.class);
        assertInstanceOf(LinkedList.class, linkedList);
        assertEquals(Arrays.asList(2, 1, 3), linkedList);

        Set<?> treeSet = (Set<?>) nodeFacade.readNode(ja, TreeSet.class);
        assertInstanceOf(TreeSet.class, treeSet);
        assertEquals(Arrays.asList(1, 2, 3), new ArrayList<>(treeSet));

        assertThrows(JsonException.class, () -> nodeFacade.readNode(ja, SortedSet.class));
    }

    @Test
    public void testDeepCopyPreservesConcreteContainers() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("a", 1);
        Object mapCopy = nodeFacade.readNode(map, Object.class, true);
        assertInstanceOf(HashMap.class, mapCopy);
        assertNotSame(map, mapCopy);

        LinkedList<Object> list = new LinkedList<>(Arrays.asList("b", "a"));
        Object listCopy = nodeFacade.readNode(list, Object.class, true);
        assertInstanceOf(LinkedList.class, listCopy);
        assertNotSame(list, listCopy);
        assertEquals(list, listCopy);

        TreeSet<Object> set = new TreeSet<>(Arrays.asList("b", "a"));
        Object setCopy = nodeFacade.readNode(set, Object.class, true);
        assertInstanceOf(TreeSet.class, setCopy);
        assertNotSame(set, setCopy);
        assertEquals(Arrays.asList("a", "b"), new ArrayList<>((Set<?>) setCopy));

        Object singletonMapCopy = nodeFacade.readNode(Collections.singletonMap("a", 1), Object.class, true);
        assertInstanceOf(LinkedHashMap.class, singletonMapCopy);
        assertEquals(Collections.singletonMap("a", 1), singletonMapCopy);

        Object fixedListCopy = nodeFacade.readNode(Arrays.asList("x", "y"), Object.class, true);
        assertInstanceOf(ArrayList.class, fixedListCopy);
        assertEquals(Arrays.asList("x", "y"), fixedListCopy);

        Object singletonSetCopy = nodeFacade.readNode(Collections.singleton("z"), Object.class, true);
        assertInstanceOf(LinkedHashSet.class, singletonSetCopy);
        assertEquals(Collections.singleton("z"), singletonSetCopy);
    }

    public static class MapperNameSource {
        public String value;
    }

    public static class MapperNameTarget {
        public String text;
        public String marker;
    }

    public static class MapperContainerSource {
        public MapperNameSource lead;
        public List<MapperNameSource> members;
    }

    public static class MapperContainerTarget {
        public MapperNameTarget lead;
        public List<MapperNameTarget> members;
    }

    @Test
    public void testReadMapperOverridesTopLevelPojoBinding() {
        SimpleNodeFacade facade = new SimpleNodeFacade(ValueFormatMapping.EMPTY, new NodeConverter<MapperNameSource, MapperNameTarget>() {
            @Override
            public Class<MapperNameSource> sourceType() {
                return MapperNameSource.class;
            }

            @Override
            public Class<MapperNameTarget> targetType() {
                return MapperNameTarget.class;
            }

            @Override
            public MapperNameTarget convert(MapperNameSource source) {
                MapperNameTarget target = new MapperNameTarget();
                target.text = source.value == null ? null : source.value.toUpperCase();
                target.marker = "mapper";
                return target;
            }
        });

        MapperNameSource source = new MapperNameSource();
        source.value = "alice";

        MapperNameTarget target = (MapperNameTarget) facade.readNode(source, MapperNameTarget.class, true);
        assertEquals("ALICE", target.text);
        assertEquals("mapper", target.marker);
    }

    @Test
    public void testReadMapperAppliesToNestedPojoAndListElements() {
        SimpleNodeFacade facade = new SimpleNodeFacade(ValueFormatMapping.EMPTY, new NodeConverter<MapperNameSource, MapperNameTarget>() {
            @Override
            public Class<MapperNameSource> sourceType() {
                return MapperNameSource.class;
            }

            @Override
            public Class<MapperNameTarget> targetType() {
                return MapperNameTarget.class;
            }

            @Override
            public MapperNameTarget convert(MapperNameSource source) {
                MapperNameTarget target = new MapperNameTarget();
                target.text = "mapped:" + source.value;
                target.marker = "nested";
                return target;
            }
        });

        MapperContainerSource source = new MapperContainerSource();
        source.lead = new MapperNameSource();
        source.lead.value = "captain";

        MapperNameSource member1 = new MapperNameSource();
        member1.value = "m1";
        MapperNameSource member2 = new MapperNameSource();
        member2.value = "m2";
        source.members = Arrays.asList(member1, member2);

        MapperContainerTarget target = (MapperContainerTarget) facade.readNode(source, MapperContainerTarget.class, true);
        assertEquals("mapped:captain", target.lead.text);
        assertEquals("nested", target.lead.marker);
        assertEquals(2, target.members.size());
        assertEquals("mapped:m1", target.members.get(0).text);
        assertEquals("mapped:m2", target.members.get(1).text);
        assertEquals("nested", target.members.get(1).marker);
    }


}
