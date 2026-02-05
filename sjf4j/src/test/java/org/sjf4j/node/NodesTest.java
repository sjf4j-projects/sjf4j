package org.sjf4j.node;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.Sjf4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public void testAsInteger() {
        assertEquals(123, Nodes.toInteger(123));
        assertEquals(123, Nodes.asInteger("123"));
        assertEquals(123, Nodes.asInteger(123.45));
        assertNull(Nodes.asInteger(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toInteger("123");
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
        JsonObject jo = new JsonObject("key", "value");
        assertEquals(jo, Nodes.toJsonObject(jo));
        assertNull(Nodes.toJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toJsonObject("not an object");
        });
    }

    @Test
    public void testToJsonObject2() {
        JsonObject jo = new JsonObject("key", "value");
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
        assertEquals(1, fromList.getInteger(0));
        
        assertNull(Nodes.toJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            Nodes.toJsonArray("not an array");
        });
    }

    @Test
    public void testToArray1() {
        String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":55,\"kk\":{\"jj\":11}},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";
        Person person = Sjf4j.fromJson(JSON_DATA, Person.class);
        log.info("person={}", person);
        Baby[] babies = person.getArray("babies", Baby.class);
        log.info("babies={}", Nodes.inspect(babies));
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

    @Test
    public void testEquals() {
        JsonObject jo = new JsonObject(
                "name", "Bob",
                "address", new JsonObject(
                        "city", "New York",
                        "street", "5th Ave"));
        Person p1 = jo.toPojo(Person.class);
        JsonObject jo1 = new JsonObject(p1);
        assertEquals(p1, jo1);
        assertEquals(jo1, p1);

        Map<String, Object> map1 = jo1.toMap();
        assertEquals(jo1, map1);
        assertNotEquals(map1, jo1);

        assertEquals(jo1.toJson(), Sjf4j.toJsonString(map1));
        assertEquals(jo1.toJson(), p1.toJson());
        assertTrue(jo1.nodeEquals(map1));
    }

    @Test
    public void testHash1() {
        JsonObject jo1 = new JsonObject(
                "name", "Bob",
                "yes", true,
                "address", new JsonObject(
                    "city", 1,
                    "street", Arrays.asList("aa", "bb")));

        JsonObject jo2 = new JsonObject(
                "yes", true,
                "name", "Bob",
                "address", new JsonObject(
                    "city", 1.0,
                    "street", Arrays.asList("aa", "bb")));

        assertNotEquals(jo1.hashCode(), jo2.hashCode());
        assertEquals(Nodes.hash(jo1), Nodes.hash(jo2));
    }

    @Test
    public void testCopy1() {
        JsonObject jo1 = JsonObject.fromJson("{\"num\":\"6\",\"duck\":[\"haha\",\"haha\"],\"attr\":{\"aa\":88,\"cc\":\"dd\",\"ee\":{\"ff\":\"uu\"},\"kk\":[1,2]},\"yo\":77}");
        JsonObject jo2 = Nodes.copy(jo1);
        JsonObject jo3 = Nodes.deepCopy(jo1);
        assertEquals(jo1, jo2);
        assertEquals(jo1, jo3);

        jo1.put("num", "7");
        assertNotEquals(jo1, jo2);
        assertNotEquals(jo1, jo3);
    }

    @Test
    public void testCopy2() {
        JsonObject jo = new JsonObject(
                "name", "Bob",
                "address", new JsonObject(
                "city", "New York",
                "street", "5th Ave"));
        Person p1 = jo.toPojo(Person.class);
        Person p2 = Nodes.copy(p1);
        Person p3 = Nodes.deepCopy(p1);
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
        JsonObject jo = new JsonObject(
                "name", "Bob",
                "friends", new String[]{"Tom", "Jay"});
        Baby b1 = jo.toPojo(Baby.class);
        Baby b2 = Nodes.copy(b1);
        Baby b3 = Nodes.deepCopy(b1);
        log.info("b1={}, b3={}", b1, b3);
        log.info("b2={}, b3={}", b2, b3);
        assertEquals(b1, b2);
        assertEquals(Sjf4j.toJsonString(b1), Sjf4j.toJsonString(b2));
        assertEquals(Sjf4j.toJsonString(b1), Sjf4j.toJsonString(b3));

        b1.friends.set(0, "Jim");
        assertEquals(Sjf4j.toJsonString(b1), Sjf4j.toJsonString(b2));
        assertNotEquals(Sjf4j.toJsonString(b1), Sjf4j.toJsonString(b3));

        b1.name = "Bro";
        assertNotEquals(Sjf4j.toJsonString(b1), Sjf4j.toJsonString(b2));
    }

    @Test
    public void testInspect1() {
        String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":55,\"kk\":{\"jj\":11}},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";
        Person person = Sjf4j.fromJson(JSON_DATA, Person.class);
        log.info("person={}", person.toString());
        log.info("person={}", person.inspect());
    }

    @Test
    public void testInspect2() {
        NodeRegistry.ValueCodecInfo vci = NodeRegistry.registerValueCodec(new ValueCodec<LocalDate, String>() {
            @Override
            public String encode(LocalDate node) {
                return node.toString();
            }

            @Override
            public LocalDate decode(String raw) {
                return LocalDate.parse(raw);
            }

            @Override
            public Class<LocalDate> getValueClass() {
                return LocalDate.class;
            }

            @Override
            public Class<String> getRawClass() {
                return String.class;
            }
        });

        LocalDate date1 = LocalDate.now();
        LocalDate date2 = Sjf4j.fromNode(date1.toString(), LocalDate.class);
        log.info("date2={}", date2);
        assertEquals(date1, date2);

        log.info("inspect={}", Nodes.inspect(date2));
        assertEquals("@LocalDate#" + date1.toString(), Nodes.inspect(date2));
    }

}

