package org.sjf4j.util;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

@Slf4j
public class TypeUtilTest {

    static class Wrapper<T> {
        T value;
        List<String>[] listArray;
        List<T> list;
        T[] array;
    }

    static class WrapperTwo<T1, T2> {
        T1 value1;
        T2 value2;
    }

    static class Nested extends Wrapper<WrapperTwo<String, Integer>> {

    }

    static class Pair<A, B> {
        Map<A, List<B>> map;
    }


    @Test
    public void testGetRawClass1() throws NoSuchFieldException {
        Class<?> raw1 = TypeUtil.getRawClass(int[].class);
        log.info("raw1={}", raw1);

        Class<?> raw2 = TypeUtil.getRawClass(String[].class);
        log.info("raw2={}", raw2);

        Class<?> raw3 = TypeUtil.getRawClass(Wrapper.class.getDeclaredField("listArray").getGenericType());
        log.info("raw3={}, isArray={}", raw3, raw3.isArray());
    }

    @Test
    public void testGetFieldType1() throws NoSuchFieldException {
        Field value = Wrapper.class.getDeclaredField("value");
        Field map = Pair.class.getDeclaredField("map");

        Type t1 = TypeUtil.getFieldType(new TypeReference<Wrapper<String>>(){}.getType(), value);
        log.info("t1={}", t1);

        Type t2 = TypeUtil.getFieldType(new TypeReference<Pair<Integer, String>>(){}.getType(), map);
        log.info("t2={}", t2);
    }

    @Test
    public void testGetFieldType2() throws NoSuchFieldException {
        Field f1 = Wrapper.class.getDeclaredField("value");
        Field f2 = Wrapper.class.getDeclaredField("list");
        Field f3 = Wrapper.class.getDeclaredField("array");

        Type t1 = new TypeReference<Wrapper<String>>(){}.getType();

        System.out.println("value → " + TypeUtil.getFieldType(t1, f1)); // String
        System.out.println("list → " + TypeUtil.getFieldType(t1, f2));  // List<String>
        System.out.println("array → " + TypeUtil.getFieldType(t1, f3)); // String[]

        // 嵌套泛型
        Field nf1 = Nested.class.getSuperclass().getDeclaredField("value");
        Type t2 = Nested.class.getGenericSuperclass();
        System.out.println("Nested.value → " + TypeUtil.getFieldType(t2, nf1));

        // WrapperTwo 测试
        Field wf1 = WrapperTwo.class.getDeclaredField("value1");
        Field wf2 = WrapperTwo.class.getDeclaredField("value2");
        Type t3 = new TypeReference<WrapperTwo<Long, Number>>(){}.getType();
        System.out.println("WrapperTwo.value1 → " + TypeUtil.getFieldType(t3, wf1)); // Long
        System.out.println("WrapperTwo.value2 → " + TypeUtil.getFieldType(t3, wf2)); // List<? extends Number>
    }


}
