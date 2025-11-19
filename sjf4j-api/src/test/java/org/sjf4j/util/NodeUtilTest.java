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
    public void testValueToString() {
        assertEquals("test", NodeUtil.valueToString("test"));
        assertEquals("a", NodeUtil.valueToString('a'));
        assertNull(NodeUtil.valueToString(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueToString(123);
        });
    }

    @Test
    public void testValueAsString() {
        assertEquals("test", NodeUtil.valueAsString("test"));
        assertEquals("a", NodeUtil.valueAsString('a'));
        assertEquals("123", NodeUtil.valueAsString(123));
        assertEquals("true", NodeUtil.valueAsString(true));
        assertNull(NodeUtil.valueAsString(null));
    }

    @Test
    public void testValueToNumber() {
        assertEquals(123, NodeUtil.valueToNumber(123));
        assertEquals(123L, NodeUtil.valueToNumber(123L));
        assertEquals(123.45, NodeUtil.valueToNumber(123.45));
        assertNull(NodeUtil.valueToNumber(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueToNumber("123");
        });
    }

    @Test
    public void testValueAsLong() {
        assertEquals(123L, NodeUtil.valueAsLong(123));
        assertEquals(123L, NodeUtil.valueAsLong(123L));
        assertEquals(123L, NodeUtil.valueAsLong(123.45));
        assertNull(NodeUtil.valueAsLong(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueAsLong("123");
        });
    }

    @Test
    public void testValueAsInteger() {
        assertEquals(123, NodeUtil.valueAsInteger(123));
        assertEquals(123, NodeUtil.valueAsInteger(123L));
        assertEquals(123, NodeUtil.valueAsInteger(123.45));
        assertNull(NodeUtil.valueAsInteger(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueAsInteger("123");
        });
    }

    @Test
    public void testValueAsDouble() {
        assertEquals(123.45, NodeUtil.valueAsDouble(123.45));
        assertEquals(123.0, NodeUtil.valueAsDouble(123));
        assertNull(NodeUtil.valueAsDouble(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueAsDouble("123.45");
        });
    }

    @Test
    public void testValueAsBigInteger() {
        assertEquals(BigInteger.valueOf(123), NodeUtil.valueAsBigInteger(123));
        assertEquals(BigInteger.valueOf(123L), NodeUtil.valueAsBigInteger(123L));
        assertNull(NodeUtil.valueAsBigInteger(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueAsBigInteger("123");
        });
    }

    @Test
    public void testValueAsBigDecimal() {
        assertEquals(new BigDecimal("123.45"), NodeUtil.valueAsBigDecimal(new BigDecimal("123.45")));
        assertEquals(new BigDecimal("123"), NodeUtil.valueAsBigDecimal(123));
        assertNull(NodeUtil.valueAsBigDecimal(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueAsBigDecimal("123.45");
        });
    }

    @Test
    public void testValueToBoolean() {
        assertTrue(NodeUtil.valueToBoolean(true));
        assertFalse(NodeUtil.valueToBoolean(false));
        assertNull(NodeUtil.valueToBoolean(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueToBoolean("true");
        });
    }

    @Test
    public void testValueToJsonObject() {
        JsonObject jo = new JsonObject("key", "value");
        assertEquals(jo, NodeUtil.valueToJsonObject(jo));
        assertNull(NodeUtil.valueToJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueToJsonObject("not an object");
        });
    }

    @Test
    public void testValueAsJsonObject() {
        JsonObject jo = new JsonObject("key", "value");
        assertEquals(jo, NodeUtil.valueAsJsonObject(jo));
        
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        JsonObject fromMap = NodeUtil.valueAsJsonObject(map);
        assertEquals("value", fromMap.getString("key"));
        
        assertNull(NodeUtil.valueAsJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueAsJsonObject("not an object");
        });
    }

    @Test
    public void testValueToJsonArray() {
        JsonArray ja = new JsonArray(new int[]{1, 2, 3});
        assertEquals(ja, NodeUtil.valueToJsonArray(ja));
        assertNull(NodeUtil.valueToJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueToJsonArray("not an array");
        });
    }

    @Test
    public void testValueAsJsonArray() {
        JsonArray ja = new JsonArray(new int[]{1, 2, 3});
        assertEquals(ja, NodeUtil.valueAsJsonArray(ja));
        
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        JsonArray fromList = NodeUtil.valueAsJsonArray(list);
        assertEquals(2, fromList.size());
        assertEquals(1, fromList.getInteger(0));
        
        assertNull(NodeUtil.valueAsJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueAsJsonArray("not an array");
        });
    }


    enum TestEnum { A, B }

    @Test
    public void testValueTo() {
        // 测试基本类型
        assertEquals(123, NodeUtil.valueTo(123, Integer.class));
        assertEquals(123L, NodeUtil.valueTo(123L, Long.class));
        assertEquals("test", NodeUtil.valueTo("test", String.class));
        
        // 测试类型转换
        assertEquals(123, NodeUtil.valueTo(123.45, Integer.class));
        assertEquals(123L, NodeUtil.valueTo(123.45, Long.class));
        
        // 测试null
        assertNull(NodeUtil.valueTo(null, String.class));
        
        // 测试enum
        assertEquals(TestEnum.A, NodeUtil.valueTo("A", TestEnum.class));
        
        // 测试不匹配的类型
        assertThrows(JsonException.class, () -> {
            NodeUtil.valueTo("not a number", Integer.class);
        });
    }

}

