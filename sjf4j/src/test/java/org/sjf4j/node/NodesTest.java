package org.sjf4j.node;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class NodesTest {

    @Test
    public void testToString() {
        assertEquals("test", Nodes.toString("test"));
        assertEquals("a", Nodes.toString('a'));
        assertNull(Nodes.toString(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toString(123);
        });
    }

    @Test
    public void testAsString() {
        assertEquals("test", Nodes.asString("test"));
        assertEquals("a", Nodes.asString('a'));
        assertEquals("123", Nodes.asString(123));
        assertEquals("true", Nodes.asString(true));
        assertNull(Nodes.asString(null));
    }

    @Test
    public void testToNumber() {
        assertEquals(123, Nodes.toNumber(123));
        assertEquals(123L, Nodes.asNumber(123L));
        assertEquals(123.45, Nodes.toNumber(123.45));
        assertNull(Nodes.toNumber(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toNumber("123");
        });
    }

    @Test
    public void testValueAsLong() {
        assertEquals(123L, Nodes.toLong(123));
        assertEquals(123L, Nodes.asLong("123"));
        assertEquals(123L, Nodes.asLong(123.45));
        assertNull(Nodes.asLong(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toLong("123");
        });
    }

    @Test
    public void testAsInt() {
        assertEquals(123, Nodes.toInt(123));
        assertEquals(123, Nodes.asInt("123"));
        assertEquals(123, Nodes.asInt(123.45));
        assertNull(Nodes.asInt(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toInt("123");
        });
    }

    @Test
    public void testAsDouble() {
        assertEquals(123.45, Nodes.toDouble(123.45));
        assertEquals(123.0, Nodes.asDouble(123));
        assertNull(Nodes.asDouble(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toDouble("123.45");
        });
    }

    @Test
    public void testAsBigInteger() {
        assertEquals(BigInteger.valueOf(123), Nodes.asBigInteger(123));
        assertEquals(BigInteger.valueOf(123L), Nodes.toBigInteger(123L));
        assertNull(Nodes.asBigInteger(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toBigInteger("123");
        });
    }

    @Test
    public void testAsBigDecimal() {
        assertEquals(new BigDecimal("123.45"), Nodes.asBigDecimal(new BigDecimal("123.45")));
        assertEquals(new BigDecimal("123"), Nodes.asBigDecimal(123));
        assertNull(Nodes.asBigDecimal(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toBigDecimal("123.45");
        });
        assertDoesNotThrow(() -> {
            Nodes.asBigDecimal("123.45");
        });
    }

    @Test
    public void testToBoolean() {
        assertTrue(Nodes.toBoolean(true));
        assertTrue(Nodes.asBoolean("true"));
        assertFalse(Nodes.toBoolean(false));
        assertNull(Nodes.toBoolean(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toBoolean("true");
        });
    }

    @Test
    public void testToJsonObject() {
        JsonObject jo = JsonObject.of("key", "value");
        assertEquals(jo, Nodes.toJsonObject(jo));
        assertNull(Nodes.toJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toJsonObject("not an object");
        });
    }

    @Test
    public void testToJsonObject2() {
        JsonObject jo = JsonObject.of("key", "value");
        assertEquals(jo, Nodes.toJsonObject(jo));
        
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        JsonObject fromMap = Nodes.toJsonObject(map);
        assertEquals("value", fromMap.getString("key"));
        
        assertNull(Nodes.toJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toJsonObject("not an object");
        });
    }

    @Test
    public void testToJsonArray() {
        JsonArray ja = new JsonArray(new int[]{1, 2, 3});
        assertEquals(ja, Nodes.toJsonArray(ja));
        assertNull(Nodes.toJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toJsonArray("not an array");
        });
    }

    @Test
    public void testToJsonArray2() {
        JsonArray ja = new JsonArray(new int[]{1, 2, 3});
        assertEquals(ja, Nodes.toJsonArray(ja));
        
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        JsonArray fromList = Nodes.toJsonArray(list);
        assertEquals(2, fromList.size());
        assertEquals(1, fromList.getInt(0));
        
        assertNull(Nodes.toJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toJsonArray("not an array");
        });
    }

    @Test
    public void testToArray1() {
        String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":55,\"kk\":{\"jj\":11}},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";
        Person person = Sjf4j.global().fromJson(JSON_DATA, Person.class);
        log.info("person={}", person);
        Baby[] babies = person.getArray("babies", Baby.class);
        log.info("babies={}", Nodes.inspect(babies));
    }

    @Test
    public void testToListWithMapper() {
        JsonArray hits = JsonArray.of(
                JsonObject.of("_id", "a"),
                JsonObject.of("_id", "b"));

        List<String> ids = Nodes.toList(hits, node -> Nodes.toJsonObject(node).getString("_id"));

        assertEquals(Arrays.asList("a", "b"), ids);
        assertNull(Nodes.toList(null, node -> node));
        assertThrows(NullPointerException.class, () -> Nodes.toList(hits, (java.util.function.Function<Object, Object>) null));
        assertThrows(JsonException.class, () -> Nodes.toList("x", node -> node));
    }

    enum TestEnum { A, B }

    @Test
    public void testTo1() {
        assertEquals(123, Nodes.to(123, Integer.class));
        assertEquals(123L, Nodes.to(123L, Long.class));
        assertEquals("test", Nodes.to("test", String.class));

        assertEquals(123, Nodes.to(123.45, Integer.class));
        assertEquals(123L, Nodes.to(123.45, Long.class));

        assertNull(Nodes.to(null, String.class));

        assertEquals(TestEnum.A, Nodes.to(TestEnum.A, TestEnum.class));
        assertEquals(TestEnum.A, Nodes.as("A", TestEnum.class));

        assertThrows(JsonException.class, () -> {
            Nodes.to("not a number", Integer.class);
        });
    }

    @Test
    public void testTo2() {
        assertEquals(123, Nodes.to(123, int.class));
        assertEquals(123L, Nodes.to(123L, long.class));
    }

    @Test
    public void testToMapImpls() {
        JsonObject jo = JsonObject.of("b", 2, "a", 1);

        Map<?, ?> hashMap = Nodes.to(jo, HashMap.class);
        assertInstanceOf(HashMap.class, hashMap);
        assertEquals(2, hashMap.get("b"));

        Map<?, ?> linkedHashMap = Nodes.to(jo, LinkedHashMap.class);
        assertInstanceOf(LinkedHashMap.class, linkedHashMap);
        assertEquals(Arrays.asList("b", "a"), new ArrayList<>(linkedHashMap.keySet()));

        Map<?, ?> treeMap = Nodes.to(jo, TreeMap.class);
        assertInstanceOf(TreeMap.class, treeMap);
        assertEquals(Arrays.asList("a", "b"), new ArrayList<>(treeMap.keySet()));

        Map<?, ?> concurrentHashMap = Nodes.to(jo, ConcurrentHashMap.class);
        assertInstanceOf(ConcurrentHashMap.class, concurrentHashMap);
        assertEquals(1, concurrentHashMap.get("a"));

        JsonObject withNull = JsonObject.of("a", null);
        assertThrows(NullPointerException.class, () -> Nodes.to(withNull, ConcurrentHashMap.class));
        assertThrows(JsonException.class, () -> Nodes.to(jo, ConcurrentMap.class));
        assertThrows(JsonException.class, () -> Nodes.to(jo, SortedMap.class));
    }

    @Test
    public void testToListImpls() {
        JsonArray ja = JsonArray.of(2, 1, 3);

        List<?> arrayList = Nodes.to(ja, ArrayList.class);
        assertInstanceOf(ArrayList.class, arrayList);
        assertEquals(Arrays.asList(2, 1, 3), arrayList);

        List<?> linkedList = Nodes.to(ja, LinkedList.class);
        assertInstanceOf(LinkedList.class, linkedList);
        assertEquals(Arrays.asList(2, 1, 3), linkedList);

        List<?> cowList = Nodes.to(ja, CopyOnWriteArrayList.class);
        assertInstanceOf(CopyOnWriteArrayList.class, cowList);
        assertEquals(Arrays.asList(2, 1, 3), cowList);
    }

    @Test
    public void testToSetImpls() {
        JsonArray ja = JsonArray.of(2, 1, 3);

        Set<?> hashSet = Nodes.to(ja, HashSet.class);
        assertInstanceOf(HashSet.class, hashSet);
        assertEquals(new HashSet<>(Arrays.asList(1, 2, 3)), hashSet);

        Set<?> linkedHashSet = Nodes.to(ja, LinkedHashSet.class);
        assertInstanceOf(LinkedHashSet.class, linkedHashSet);
        assertEquals(Arrays.asList(2, 1, 3), new ArrayList<>(linkedHashSet));

        Set<?> treeSet = Nodes.to(ja, TreeSet.class);
        assertInstanceOf(TreeSet.class, treeSet);
        assertEquals(Arrays.asList(1, 2, 3), new ArrayList<>(treeSet));

        assertThrows(JsonException.class, () -> Nodes.to(ja, SortedSet.class));
    }

    @Test
    public void testFastjson2JSONObjectParse() {
        JSONObject jo = JSONObject.parseObject("{\"name\":\"Alice\",\"age\":30,\"info\":{\"city\":\"SZ\"}}");

        Person person = Nodes.to(jo, Person.class);
        assertEquals("Alice", person.getName());
        assertEquals(30, person.getAge());
        assertEquals("SZ", person.getInfo().getString("city"));

        Map<?, ?> map = Nodes.to(jo, LinkedHashMap.class);
        assertInstanceOf(LinkedHashMap.class, map);
        assertEquals("Alice", map.get("name"));
    }

    @Getter
    @Setter
    @AnyOf(value = {
            @AnyOf.Mapping(value = Cat.class, when = "cat"),
            @AnyOf.Mapping(value = Dog.class, when = "dog")
    }, key = "kind")
    public static class Animal {
        private String kind;
        private String name;
    }

    @Getter
    @Setter
    public static class Cat extends Animal {
        private int lives;
    }

    @Getter
    @Setter
    public static class Dog extends Animal {
        private int bark;
    }

    public static class Zoo {
        @AnyOf(value = {
                @AnyOf.Mapping(value = Cat.class, when = "cat"),
                @AnyOf.Mapping(value = Dog.class, when = "dog")
        }, key = "kind")
        public Animal pet;
    }

    @Test
    void testNodesToPojoAnyOfRoot() {
        JsonObject src = JsonObject.of("kind", "cat", "name", "Nana", "lives", 7);
        Animal animal = Nodes.to(src, Animal.class);
        assertInstanceOf(Cat.class, animal);
        assertEquals("Nana", animal.getName());
        assertEquals(7, ((Cat) animal).getLives());
    }

    @Test
    void testNodesToPojoAnyOfField() {
        JsonObject src = JsonObject.of("pet", JsonObject.of("kind", "dog", "name", "Bobo", "bark", 3));
        Zoo zoo = Nodes.to(src, Zoo.class);
        assertInstanceOf(Dog.class, zoo.pet);
        assertEquals("Bobo", zoo.pet.getName());
        assertEquals(3, ((Dog) zoo.pet).getBark());
    }




    /// Basic

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Baby extends JsonObject {
        private String name;
        private int month;
        public List<String> friends;
    }

    @Getter @Setter
    @NoArgsConstructor
    public static class Person extends JsonObject {
        private String name;
        private int age;
        private JsonObject info;
        private List<Baby> babies;
        private Address address;
    }

    public static class Address extends JsonObject {
        public String city;
        public String street;
    }

    public static class CtorOnlyPojo {
        public final String id;
        public final int level;
        public String note;
        public List<String> tags;

        @NodeCreator
        public CtorOnlyPojo(@NodeProperty("id") String id, @NodeProperty("level") int level) {
            this.id = id;
            this.level = level;
        }
    }

    @Test
    public void testEquals() {
        JsonObject jo = JsonObject.of(
                "name", "Bob",
                "address", JsonObject.of(
                        "city", "New York",
                        "street", "5th Ave"));
        Person p1 = jo.toPojo(Person.class);
        JsonObject jo1 = new JsonObject(p1);
        assertTrue(Nodes.equals(p1,jo1));
        assertTrue(Nodes.equals(jo1, p1));

        Map<String, Object> map1 = jo1.toMap();
        assertNotEquals(jo1, map1);
        assertTrue(Nodes.equals(p1, map1));
        assertTrue(Nodes.equals(map1, jo1));
        assertTrue(Nodes.equals(jo1, map1));

        log.info("map1={}", Nodes.toJsonObject(map1));
        assertEquals(jo1.toJson(), Sjf4j.global().toJsonString(map1));
        assertEquals(jo1.toJson(), p1.toJson());
        assertTrue(jo1.nodeEquals(map1));
    }

    @Test
    public void testHash1() {
        JsonObject jo1 = JsonObject.of(
                "name", "Bob",
                "yes", true,
                "address", JsonObject.of(
                    "city", 1,
                    "street", Arrays.asList("aa", "bb")));

        JsonObject jo2 = JsonObject.of(
                "yes", true,
                "name", "Bob",
                "address", JsonObject.of(
                    "city", 1.0,
                    "street", Arrays.asList("aa", "bb")));

        assertNotEquals(jo1.hashCode(), jo2.hashCode());
        assertEquals(Nodes.hash(jo1), Nodes.hash(jo2));
    }

    @Test
    public void testCopy1() {
        JsonObject jo1 = JsonObject.fromJson("{\"num\":\"6\",\"duck\":[\"haha\",\"haha\"],\"attr\":{\"aa\":88,\"cc\":\"dd\",\"ee\":{\"ff\":\"uu\"},\"kk\":[1,2]},\"yo\":77}");
        JsonObject jo2 = Nodes.copy(jo1);
        JsonObject jo3 = Sjf4j.global().deepNode(jo1);
        assertEquals(jo1, jo2);
        assertEquals(jo1, jo3);

        jo1.put("num", "7");
        assertNotEquals(jo1, jo2);
        assertNotEquals(jo1, jo3);
    }

    @Test
    public void testCopy2() {
        JsonObject jo = JsonObject.of(
                "name", "Bob",
                "address", JsonObject.of(
                "city", "New York",
                "street", "5th Ave"));
        Person p1 = jo.toPojo(Person.class);
        Person p2 = Nodes.copy(p1);
        Person p3 = Sjf4j.global().deepNode(p1);
        assertEquals(p1, p2);
        assertEquals(p1, p3);

        p1.address.city = "Beijing";
        assertEquals(p1, p2);
        assertNotEquals(p1, p3);

        p1.name = "Tom";
        assertNotEquals(p1, p2);
    }

    @Test
    public void testCopy3() {
        JsonObject jo = JsonObject.of(
                "name", "Bob",
                "friends", new String[]{"Tom", "Jay"});
        Baby b1 = jo.toPojo(Baby.class);
        Baby b2 = Nodes.copy(b1);
        Baby b3 = Sjf4j.global().deepNode(b1);
        log.info("b1={}, b3={}", b1, b3);
        log.info("b2={}, b3={}", b2, b3);
        assertEquals(b1, b2);
        assertEquals(Sjf4j.global().toJsonString(b1), Sjf4j.global().toJsonString(b2));
        assertEquals(Sjf4j.global().toJsonString(b1), Sjf4j.global().toJsonString(b3));

        b1.friends.set(0, "Jim");
        assertEquals(Sjf4j.global().toJsonString(b1), Sjf4j.global().toJsonString(b2));
        assertNotEquals(Sjf4j.global().toJsonString(b1), Sjf4j.global().toJsonString(b3));

        b1.name = "Bro";
        assertNotEquals(Sjf4j.global().toJsonString(b1), Sjf4j.global().toJsonString(b2));
    }

    @Test
    public void testCopyMapImpls() {
        LinkedHashMap<String, Object> linked = new LinkedHashMap<>();
        linked.put("b", 2);
        linked.put("a", 1);
        Map<?, ?> linkedCopy = Nodes.copy(linked);
        assertInstanceOf(LinkedHashMap.class, linkedCopy);
        assertNotSame(linked, linkedCopy);
        assertEquals(Arrays.asList("b", "a"), new ArrayList<>(linkedCopy.keySet()));

        ConcurrentHashMap<String, Object> concurrent = new ConcurrentHashMap<>();
        concurrent.put("x", 7);
        Map<?, ?> concurrentCopy = Nodes.copy(concurrent);
        assertInstanceOf(ConcurrentHashMap.class, concurrentCopy);
        assertNotSame(concurrent, concurrentCopy);
        assertEquals(7, concurrentCopy.get("x"));

        TreeMap<String, Object> sorted = new TreeMap<>();
        sorted.put("b", 2);
        sorted.put("a", 1);
        Map<?, ?> sortedCopy = Nodes.copy(sorted);
        assertInstanceOf(TreeMap.class, sortedCopy);
        assertEquals(Arrays.asList("a", "b"), new ArrayList<>(sortedCopy.keySet()));
    }

    @Test
    public void testCopyListImpls() {
        ArrayList<Object> arrayList = new ArrayList<>(Arrays.asList("b", "a"));
        List<?> arrayListCopy = Nodes.copy(arrayList);
        assertInstanceOf(ArrayList.class, arrayListCopy);
        assertNotSame(arrayList, arrayListCopy);
        assertEquals(arrayList, arrayListCopy);

        LinkedList<Object> linkedList = new LinkedList<>(Arrays.asList("b", "a"));
        List<?> linkedListCopy = Nodes.copy(linkedList);
        assertInstanceOf(LinkedList.class, linkedListCopy);
        assertNotSame(linkedList, linkedListCopy);
        assertEquals(linkedList, linkedListCopy);

        CopyOnWriteArrayList<Object> cowList = new CopyOnWriteArrayList<>(Arrays.asList("b", "a"));
        List<?> cowListCopy = Nodes.copy(cowList);
        assertInstanceOf(CopyOnWriteArrayList.class, cowListCopy);
        assertNotSame(cowList, cowListCopy);
        assertEquals(cowList, cowListCopy);

        List<?> fixedListCopy = Nodes.copy(Arrays.asList("x", "y"));
        assertInstanceOf(ArrayList.class, fixedListCopy);
        assertEquals(Arrays.asList("x", "y"), fixedListCopy);
    }

    @Test
    public void testCopySetImpls() {
        LinkedHashSet<Object> linkedSet = new LinkedHashSet<>(Arrays.asList("b", "a"));
        Set<?> linkedSetCopy = Nodes.copy(linkedSet);
        assertInstanceOf(LinkedHashSet.class, linkedSetCopy);
        assertNotSame(linkedSet, linkedSetCopy);
        assertEquals(Arrays.asList("b", "a"), new ArrayList<>(linkedSetCopy));

        TreeSet<Object> sortedSet = new TreeSet<>(Arrays.asList("b", "a"));
        Set<?> sortedSetCopy = Nodes.copy(sortedSet);
        assertInstanceOf(TreeSet.class, sortedSetCopy);
        assertNotSame(sortedSet, sortedSetCopy);
        assertEquals(Arrays.asList("a", "b"), new ArrayList<>(sortedSetCopy));

        Set<?> singletonSetCopy = Nodes.copy(Collections.singleton("z"));
        assertInstanceOf(LinkedHashSet.class, singletonSetCopy);
        assertEquals(Collections.singleton("z"), singletonSetCopy);
    }

    @Test
    public void testCopyCtorOnlyPojo() {
        CtorOnlyPojo p1 = new CtorOnlyPojo("user-1", 7);
        p1.note = "hello";
        p1.tags = new ArrayList<>(Arrays.asList("a", "b"));

        CtorOnlyPojo p2 = Nodes.copy(p1);

        assertNotSame(p1, p2);
        assertEquals(p1.id, p2.id);
        assertEquals(p1.level, p2.level);
        assertEquals(p1.note, p2.note);
        assertSame(p1.tags, p2.tags);

        p1.note = "changed";
        assertEquals("hello", p2.note);

        p1.tags.set(0, "z");
        assertEquals("z", p2.tags.get(0));
    }

    @Test
    public void testAllMatchInArray() {
        JsonArray ja = JsonArray.of(2, 4, 6);

        assertTrue(Nodes.allMatchInArray(ja, (idx, value) -> ((Number) value).intValue() % 2 == 0));
        assertFalse(Nodes.allMatchInArray(ja, (idx, value) -> idx < 2));
        assertTrue(Nodes.allMatchInArray(JsonArray.of(), (idx, value) -> false));
    }

    @Test
    public void testNoneMatchInArray() {
        List<Object> list = Arrays.asList(1, 2, 3);

        assertTrue(Nodes.noneMatchInArray(list, (idx, value) -> ((Number) value).intValue() > 3));
        assertFalse(Nodes.noneMatchInArray(list, (idx, value) -> idx == 1 && Objects.equals(value, 2)));
        assertTrue(Nodes.noneMatchInArray(new int[0], (idx, value) -> true));
    }

    @Test
    public void testReplaceInObject() {
        JsonObject jo = JsonObject.of("name", "Alice", "age", 18);

        assertFalse(Nodes.replaceInObject(jo, (key, value) -> value));
        assertEquals("Alice", jo.getString("name"));
        assertEquals(18, jo.getInt("age"));

        assertTrue(Nodes.replaceInObject(jo, (key, value) -> "age".equals(key) ? 20 : value));
        assertEquals("Alice", jo.getString("name"));
        assertEquals(20, jo.getInt("age"));

        Person person = new Person();
        person.setName("Bob");
        person.setAge(21);

        assertFalse(Nodes.replaceInObject(person, (key, value) -> value));
        assertTrue(Nodes.replaceInObject(person, (key, value) -> "name".equals(key) ? "Tom" : value));
        assertEquals("Tom", person.getName());
        assertEquals(21, person.getAge());
    }

    @Test
    public void testInspect1() {
        String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":55,\"kk\":{\"jj\":11}},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";
        Person person = Sjf4j.global().fromJson(JSON_DATA, Person.class);
        log.info("person={}", person.toString());
        log.info("person={}", person.inspect());
    }

    @Test
    public void testInspect2() {
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.overrideValueCodec(new ValueCodec<LocalDate, String>() {
            @Override
            public String valueToRaw(LocalDate node) {
                return node.toString();
            }

            @Override
            public LocalDate rawToValue(String raw) {
                return LocalDate.parse(raw);
            }

            @Override
            public Class<LocalDate> valueClass() {
                return LocalDate.class;
            }

            @Override
            public Class<String> rawClass() {
                return String.class;
            }
        });

        LocalDate date1 = LocalDate.now();
        LocalDate date2 = Sjf4j.global().fromNode(date1.toString(), LocalDate.class);
        log.info("date2={}", date2);
        assertEquals(date1, date2);

        log.info("inspect={}", Nodes.inspect(date2));
        assertEquals("@LocalDate#" + date1.toString(), Nodes.inspect(date2));
    }

}
