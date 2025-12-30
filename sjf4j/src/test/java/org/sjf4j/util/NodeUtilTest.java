package org.sjf4j.util;

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
import org.sjf4j.node.NodeConverter;
import org.sjf4j.node.NodeRegistry;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class NodeUtilTest {

    @Test
    public void testToString() {
        assertEquals("test", NodeUtil.toString("test"));
        assertEquals("a", NodeUtil.toString('a'));
        assertNull(NodeUtil.toString(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toString(123);
        });
    }

    @Test
    public void testAsString() {
        assertEquals("test", NodeUtil.asString("test"));
        assertEquals("a", NodeUtil.asString('a'));
        assertEquals("123", NodeUtil.asString(123));
        assertEquals("true", NodeUtil.asString(true));
        assertNull(NodeUtil.asString(null));
    }

    @Test
    public void testToNumber() {
        assertEquals(123, NodeUtil.toNumber(123));
        assertEquals(123L, NodeUtil.asNumber(123L));
        assertEquals(123.45, NodeUtil.toNumber(123.45));
        assertNull(NodeUtil.toNumber(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toNumber("123");
        });
    }

    @Test
    public void testValueAsLong() {
        assertEquals(123L, NodeUtil.toLong(123));
        assertEquals(123L, NodeUtil.asLong("123"));
        assertEquals(123L, NodeUtil.asLong(123.45));
        assertNull(NodeUtil.asLong(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toLong("123");
        });
    }

    @Test
    public void testAsInteger() {
        assertEquals(123, NodeUtil.toInteger(123));
        assertEquals(123, NodeUtil.asInteger("123"));
        assertEquals(123, NodeUtil.asInteger(123.45));
        assertNull(NodeUtil.asInteger(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toInteger("123");
        });
    }

    @Test
    public void testAsDouble() {
        assertEquals(123.45, NodeUtil.toDouble(123.45));
        assertEquals(123.0, NodeUtil.asDouble(123));
        assertNull(NodeUtil.asDouble(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toDouble("123.45");
        });
    }

    @Test
    public void testAsBigInteger() {
        assertEquals(BigInteger.valueOf(123), NodeUtil.asBigInteger(123));
        assertEquals(BigInteger.valueOf(123L), NodeUtil.toBigInteger(123L));
        assertNull(NodeUtil.asBigInteger(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toBigInteger("123");
        });
    }

    @Test
    public void testAsBigDecimal() {
        assertEquals(new BigDecimal("123.45"), NodeUtil.asBigDecimal(new BigDecimal("123.45")));
        assertEquals(new BigDecimal("123"), NodeUtil.asBigDecimal(123));
        assertNull(NodeUtil.asBigDecimal(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toBigDecimal("123.45");
        });
        assertDoesNotThrow(() -> {
            NodeUtil.asBigDecimal("123.45");
        });
    }

    @Test
    public void testToBoolean() {
        assertTrue(NodeUtil.toBoolean(true));
        assertTrue(NodeUtil.asBoolean("true"));
        assertFalse(NodeUtil.toBoolean(false));
        assertNull(NodeUtil.toBoolean(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toBoolean("true");
        });
    }

    @Test
    public void testToJsonObject() {
        JsonObject jo = new JsonObject("key", "value");
        assertEquals(jo, NodeUtil.toJsonObject(jo));
        assertNull(NodeUtil.toJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toJsonObject("not an object");
        });
    }

    @Test
    public void testAsJsonObject() {
        JsonObject jo = new JsonObject("key", "value");
        assertEquals(jo, NodeUtil.asJsonObject(jo));
        
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        JsonObject fromMap = NodeUtil.asJsonObject(map);
        assertEquals("value", fromMap.getString("key"));
        
        assertNull(NodeUtil.asJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.asJsonObject("not an object");
        });
    }

    @Test
    public void testToJsonArray() {
        JsonArray ja = new JsonArray(new int[]{1, 2, 3});
        assertEquals(ja, NodeUtil.toJsonArray(ja));
        assertNull(NodeUtil.toJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toJsonArray("not an array");
        });
    }

    @Test
    public void testAsJsonArray() {
        JsonArray ja = new JsonArray(new int[]{1, 2, 3});
        assertEquals(ja, NodeUtil.asJsonArray(ja));
        
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        JsonArray fromList = NodeUtil.asJsonArray(list);
        assertEquals(2, fromList.size());
        assertEquals(1, fromList.getInteger(0));
        
        assertNull(NodeUtil.asJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.asJsonArray("not an array");
        });
    }


    enum TestEnum { A, B }

    @Test
    public void testTo() {
        // 测试基本类型
        assertEquals(123, NodeUtil.to(123, Integer.class));
        assertEquals(123L, NodeUtil.to(123L, Long.class));
        assertEquals("test", NodeUtil.to("test", String.class));
        
        // 测试类型转换
        assertEquals(123, NodeUtil.to(123.45, Integer.class));
        assertEquals(123L, NodeUtil.to(123.45, Long.class));
        
        // 测试null
        assertNull(NodeUtil.to(null, String.class));
        
        // 测试enum
        assertEquals(TestEnum.A, NodeUtil.to(TestEnum.A, TestEnum.class));
        assertEquals(TestEnum.A, NodeUtil.as("A", TestEnum.class));
        
        // 测试不匹配的类型
        assertThrows(JsonException.class, () -> {
            NodeUtil.to("not a number", Integer.class);
        });
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
        Person p1 = jo.toNode(Person.class, false);
        JsonObject jo1 = new JsonObject(p1);
        assertNotEquals(p1, jo1);
        assertNotEquals(jo1, p1);

        Map<String, Object> map1 = jo1.toMap();
        assertEquals(jo1, map1);
        assertNotEquals(map1, jo1);
    }


    @Test
    public void testCopy1() {
        JsonObject jo1 = JsonObject.fromJson("{\"num\":\"6\",\"duck\":[\"haha\",\"haha\"],\"attr\":{\"aa\":88,\"cc\":\"dd\",\"ee\":{\"ff\":\"uu\"},\"kk\":[1,2]},\"yo\":77}");
        JsonObject jo2 = NodeUtil.copy(jo1);
        JsonObject jo3 = NodeUtil.deepCopy(jo1);
        assertEquals(jo1, jo2);
        assertEquals(jo1, jo3);

        jo1.put("num", "7");
        assertEquals(jo1, jo2);
        assertNotEquals(jo1, jo3);
    }

    @Test
    public void testCopy2() {
        JsonObject jo = new JsonObject(
                "name", "Bob",
                "address", new JsonObject(
                "city", "New York",
                "street", "5th Ave"));
        Person p1 = jo.toNode(Person.class, true);
        Person p2 = NodeUtil.copy(p1);
        Person p3 = NodeUtil.deepCopy(p1);
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
        Baby b1 = jo.toNode(Baby.class, false);
        Baby b2 = NodeUtil.copy(b1);
        Baby b3 = NodeUtil.deepCopy(b1);
        log.info("b1={}, b3={}", b1, b3);
        log.info("b2={}, b3={}", b2, b3);
        assertEquals(b1, b2);
        assertEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b2));
        assertEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b3));

        b1.friends.set(0, "Jim");
        assertEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b2));
        assertNotEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b3));

        b1.name = "Bro";
        assertNotEquals(Sjf4j.toJson(b1), Sjf4j.toJson(b2));
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
        NodeRegistry.ConvertibleInfo ci = NodeRegistry.registerConvertible(new NodeConverter<LocalDate, String>() {
            @Override
            public String convert(LocalDate node) {
                return node.toString();
            }

            @Override
            public LocalDate unconvert(String raw) {
                return LocalDate.parse(raw);
            }

            @Override
            public Class<LocalDate> getNodeClass() {
                return LocalDate.class;
            }

            @Override
            public Class<String> getRawClass() {
                return String.class;
            }
        });

        LocalDate date1 = LocalDate.now();
        LocalDate date2 = Sjf4j.fromNode(date1.toString(), LocalDate.class, false);
        log.info("date2={}", date2);
        assertEquals(date1, date2);

        log.info("inspect={}", NodeUtil.inspect(date2));
        assertEquals("!LocalDate#" + date1.toString(), NodeUtil.inspect(date2));
    }

}

