package org.sjf4j.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;

import java.math.BigDecimal;
import java.math.BigInteger;
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
        assertEquals(123L, NodeUtil.toNumber(123L));
        assertEquals(123.45, NodeUtil.toNumber(123.45));
        assertNull(NodeUtil.toNumber(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.toNumber("123");
        });
    }

    @Test
    public void testValueAsLong() {
        assertEquals(123L, NodeUtil.asLong(123));
        assertEquals(123L, NodeUtil.asLong(123L));
        assertEquals(123L, NodeUtil.asLong(123.45));
        assertNull(NodeUtil.asLong(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.asLong("123");
        });
    }

    @Test
    public void testAsInteger() {
        assertEquals(123, NodeUtil.asInteger(123));
        assertEquals(123, NodeUtil.asInteger(123L));
        assertEquals(123, NodeUtil.asInteger(123.45));
        assertNull(NodeUtil.asInteger(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.asInteger("123");
        });
    }

    @Test
    public void testAsDouble() {
        assertEquals(123.45, NodeUtil.asDouble(123.45));
        assertEquals(123.0, NodeUtil.asDouble(123));
        assertNull(NodeUtil.asDouble(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.asDouble("123.45");
        });
    }

    @Test
    public void testAsBigInteger() {
        assertEquals(BigInteger.valueOf(123), NodeUtil.asBigInteger(123));
        assertEquals(BigInteger.valueOf(123L), NodeUtil.asBigInteger(123L));
        assertNull(NodeUtil.asBigInteger(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.asBigInteger("123");
        });
    }

    @Test
    public void testAsBigDecimal() {
        assertEquals(new BigDecimal("123.45"), NodeUtil.asBigDecimal(new BigDecimal("123.45")));
        assertEquals(new BigDecimal("123"), NodeUtil.asBigDecimal(123));
        assertNull(NodeUtil.asBigDecimal(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.asBigDecimal("123.45");
        });
    }

    @Test
    public void testToBoolean() {
        assertTrue(NodeUtil.toBoolean(true));
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
        assertEquals(TestEnum.A, NodeUtil.to("A", TestEnum.class));
        
        // 测试不匹配的类型
        assertThrows(JsonException.class, () -> {
            NodeUtil.to("not a number", Integer.class);
        });
    }

}

