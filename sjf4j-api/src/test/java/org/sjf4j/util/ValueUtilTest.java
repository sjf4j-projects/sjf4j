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
public class ValueUtilTest {

    @Test
    public void testValueToString() {
        assertEquals("test", ValueUtil.valueToString("test"));
        assertEquals("a", ValueUtil.valueToString('a'));
        assertNull(ValueUtil.valueToString(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueToString(123);
        });
    }

    @Test
    public void testValueAsString() {
        assertEquals("test", ValueUtil.valueAsString("test"));
        assertEquals("a", ValueUtil.valueAsString('a'));
        assertEquals("123", ValueUtil.valueAsString(123));
        assertEquals("true", ValueUtil.valueAsString(true));
        assertNull(ValueUtil.valueAsString(null));
    }

    @Test
    public void testValueToNumber() {
        assertEquals(123, ValueUtil.valueToNumber(123));
        assertEquals(123L, ValueUtil.valueToNumber(123L));
        assertEquals(123.45, ValueUtil.valueToNumber(123.45));
        assertNull(ValueUtil.valueToNumber(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueToNumber("123");
        });
    }

    @Test
    public void testValueAsLong() {
        assertEquals(123L, ValueUtil.valueAsLong(123));
        assertEquals(123L, ValueUtil.valueAsLong(123L));
        assertEquals(123L, ValueUtil.valueAsLong(123.45));
        assertNull(ValueUtil.valueAsLong(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueAsLong("123");
        });
    }

    @Test
    public void testValueAsInteger() {
        assertEquals(123, ValueUtil.valueAsInteger(123));
        assertEquals(123, ValueUtil.valueAsInteger(123L));
        assertEquals(123, ValueUtil.valueAsInteger(123.45));
        assertNull(ValueUtil.valueAsInteger(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueAsInteger("123");
        });
    }

    @Test
    public void testValueAsDouble() {
        assertEquals(123.45, ValueUtil.valueAsDouble(123.45));
        assertEquals(123.0, ValueUtil.valueAsDouble(123));
        assertNull(ValueUtil.valueAsDouble(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueAsDouble("123.45");
        });
    }

    @Test
    public void testValueAsBigInteger() {
        assertEquals(BigInteger.valueOf(123), ValueUtil.valueAsBigInteger(123));
        assertEquals(BigInteger.valueOf(123L), ValueUtil.valueAsBigInteger(123L));
        assertNull(ValueUtil.valueAsBigInteger(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueAsBigInteger("123");
        });
    }

    @Test
    public void testValueAsBigDecimal() {
        assertEquals(new BigDecimal("123.45"), ValueUtil.valueAsBigDecimal(new BigDecimal("123.45")));
        assertEquals(new BigDecimal("123"), ValueUtil.valueAsBigDecimal(123));
        assertNull(ValueUtil.valueAsBigDecimal(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueAsBigDecimal("123.45");
        });
    }

    @Test
    public void testValueToBoolean() {
        assertTrue(ValueUtil.valueToBoolean(true));
        assertFalse(ValueUtil.valueToBoolean(false));
        assertNull(ValueUtil.valueToBoolean(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueToBoolean("true");
        });
    }

    @Test
    public void testValueToJsonObject() {
        JsonObject jo = new JsonObject("key", "value");
        assertEquals(jo, ValueUtil.valueToJsonObject(jo));
        assertNull(ValueUtil.valueToJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueToJsonObject("not an object");
        });
    }

    @Test
    public void testValueAsJsonObject() {
        JsonObject jo = new JsonObject("key", "value");
        assertEquals(jo, ValueUtil.valueAsJsonObject(jo));
        
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        JsonObject fromMap = ValueUtil.valueAsJsonObject(map);
        assertEquals("value", fromMap.getString("key"));
        
        assertNull(ValueUtil.valueAsJsonObject(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueAsJsonObject("not an object");
        });
    }

    @Test
    public void testValueToJsonArray() {
        JsonArray ja = new JsonArray(new int[]{1, 2, 3});
        assertEquals(ja, ValueUtil.valueToJsonArray(ja));
        assertNull(ValueUtil.valueToJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueToJsonArray("not an array");
        });
    }

    @Test
    public void testValueAsJsonArray() {
        JsonArray ja = new JsonArray(new int[]{1, 2, 3});
        assertEquals(ja, ValueUtil.valueAsJsonArray(ja));
        
        List<Object> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        JsonArray fromList = ValueUtil.valueAsJsonArray(list);
        assertEquals(2, fromList.size());
        assertEquals(1, fromList.getInteger(0));
        
        assertNull(ValueUtil.valueAsJsonArray(null));
        
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueAsJsonArray("not an array");
        });
    }


    enum TestEnum { A, B }

    @Test
    public void testValueTo() {
        // 测试基本类型
        assertEquals(123, ValueUtil.valueTo(123, Integer.class));
        assertEquals(123L, ValueUtil.valueTo(123L, Long.class));
        assertEquals("test", ValueUtil.valueTo("test", String.class));
        
        // 测试类型转换
        assertEquals(123, ValueUtil.valueTo(123.45, Integer.class));
        assertEquals(123L, ValueUtil.valueTo(123.45, Long.class));
        
        // 测试null
        assertNull(ValueUtil.valueTo(null, String.class));
        
        // 测试enum
        assertEquals(TestEnum.A, ValueUtil.valueTo("A", TestEnum.class));
        
        // 测试不匹配的类型
        assertThrows(JsonException.class, () -> {
            ValueUtil.valueTo("not a number", Integer.class);
        });
    }

}

