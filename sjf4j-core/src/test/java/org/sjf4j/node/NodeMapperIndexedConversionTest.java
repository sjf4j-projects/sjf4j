package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.NodeMapperFixtures.IntJajo;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
class NodeMapperIndexedConversionTest {
    @Test
    void convertsListJsonArrayAndJavaArraysWithoutIndexedAdapters() {
        Type integers = new TypeReference<List<Integer>>() {}.getType();
        Type longSet = new TypeReference<Set<Long>>() {}.getType();

        Object[] sources = {
                Arrays.asList(1, 2, 2),
                JsonArray.of(1, 2, 2),
                new int[]{1, 2, 2},
                new Integer[]{1, 2, 2}
        };

        for (Object source : sources) {
            List<Integer> list = (List<Integer>) NodeMapper.convert(source, integers, false, RuntimeContext.EMPTY);
            assertEquals(Arrays.asList(1, 2, 2), list);

            JsonArray jsonArray = (JsonArray) NodeMapper.convert(source, JsonArray.class, false, RuntimeContext.EMPTY);
            assertEquals(3, jsonArray.size());
            assertEquals(2, jsonArray.getNode(2));

            IntJajo jajo = (IntJajo) NodeMapper.convert(source, IntJajo.class, false, RuntimeContext.EMPTY);
            assertEquals(3, jajo.size());
            assertEquals(2, jajo.getNode(1));

            int[] array = (int[]) NodeMapper.convert(source, int[].class, false, RuntimeContext.EMPTY);
            assertArrayEquals(new int[]{1, 2, 2}, array);

            Set<Long> set = (Set<Long>) NodeMapper.convert(source, longSet, false, RuntimeContext.EMPTY);
            assertEquals(new LinkedHashSet<>(Arrays.asList(1L, 2L)), set);
        }
    }

    @Test
    void indexedConversionPreservesNullsAndNestedErrorPaths() {
        for (Object source : new Object[]{
                Arrays.asList(1, null),
                JsonArray.of(1, null),
                new Integer[]{1, null}
        }) {
            List<Integer> list = (List<Integer>) NodeMapper.convert(
                    source, new TypeReference<List<Integer>>() {}.getType(), false, RuntimeContext.EMPTY);
            assertEquals(Arrays.asList(1, null), list);
        }

        for (Object source : new Object[]{
                Arrays.asList(1, true),
                JsonArray.of(1, true),
                new Object[]{1, true}
        }) {
            BindingException error = assertThrows(BindingException.class,
                    () -> NodeMapper.convert(source, new TypeReference<List<Integer>>() {}.getType(), false, RuntimeContext.EMPTY));
            assertTrue(error.getMessage().contains("$[1]"));
        }
    }

    @Test
    void indexedLongAndObjectArrayFastPathsPreserveMappingSemantics() {
        Type integers = new TypeReference<List<Integer>>() {}.getType();
        Type longs = new TypeReference<List<Long>>() {}.getType();

        for (Object source : new Object[]{
                new long[]{1, 2, 3},
                new Long[]{1L, 2L, 3L},
                new int[]{1, 2, 3},
                new Integer[]{1, 2, 3}
        }) {
            assertEquals(Arrays.asList(1, 2, 3),
                    NodeMapper.convert(source, integers, false, RuntimeContext.EMPTY));
            assertEquals(Arrays.asList(1L, 2L, 3L),
                    NodeMapper.convert(source, longs, true, RuntimeContext.EMPTY));
            assertArrayEquals(new int[]{1, 2, 3},
                    (int[]) NodeMapper.convert(source, int[].class, false, RuntimeContext.EMPTY));
        }

        assertEquals(Arrays.asList(1, null, 3),
                NodeMapper.convert(new Long[]{1L, null, 3L}, integers, false, RuntimeContext.EMPTY));

        BindingException overflow = assertThrows(BindingException.class,
                () -> NodeMapper.convert(
                        new long[]{1L, ((long) Integer.MAX_VALUE) + 1},
                        integers, false, RuntimeContext.EMPTY));
        assertTrue(overflow.getMessage().contains("$[1]"));

        BindingException shortOverflow = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new int[]{1, Short.MAX_VALUE + 1},
                        new TypeReference<List<Short>>() {}.getType(), false, RuntimeContext.EMPTY));
        assertTrue(shortOverflow.getMessage().contains("$[1]"));

        BindingException incompatible = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new Object[]{1, true}, integers, false, RuntimeContext.EMPTY));
        assertTrue(incompatible.getMessage().contains("$[1]"));
    }

    @Test
    void indexedPrimitiveArrayFastPathsCoverEveryElementType() {
        Type ints = new TypeReference<List<Integer>>() {}.getType();
        Type doubles = new TypeReference<List<Double>>() {}.getType();
        Type floats = new TypeReference<List<Float>>() {}.getType();
        Type booleans = new TypeReference<List<Boolean>>() {}.getType();
        Type characters = new TypeReference<List<Character>>() {}.getType();
        Type strings = new TypeReference<List<String>>() {}.getType();

        assertEquals(Arrays.asList(1, 2, 3),
                NodeMapper.convert(new byte[]{1, 2, 3}, ints, false, RuntimeContext.EMPTY));
        assertEquals(Arrays.asList(1, 2, 3),
                NodeMapper.convert(new short[]{1, 2, 3}, ints, false, RuntimeContext.EMPTY));
        assertEquals(Arrays.asList(1.25d, 2.5d),
                NodeMapper.convert(new float[]{1.25f, 2.5f}, doubles, false, RuntimeContext.EMPTY));
        assertEquals(Arrays.asList(1.25f, 2.5f),
                NodeMapper.convert(new double[]{1.25d, 2.5d}, floats, false, RuntimeContext.EMPTY));
        assertEquals(Arrays.asList(true, false, true),
                NodeMapper.convert(new boolean[]{true, false, true}, booleans, false, RuntimeContext.EMPTY));
        assertEquals(Arrays.asList('A', 'B'),
                NodeMapper.convert(new char[]{'A', 'B'}, characters, false, RuntimeContext.EMPTY));
        assertEquals(Arrays.asList("A", "B"),
                NodeMapper.convert(new char[]{'A', 'B'}, strings, false, RuntimeContext.EMPTY));

        // Direct primitive reads must keep indexed paths on checked numeric failures.
        BindingException overflow = assertThrows(BindingException.class,
                () -> NodeMapper.convert(new double[]{1.0, Double.MAX_VALUE}, floats, false, RuntimeContext.EMPTY));
        assertTrue(overflow.getMessage().contains("$[1]"));

        // Non-List targets still use the same generic target-conversion engine.
        assertArrayEquals(new int[]{1, 2},
                (int[]) NodeMapper.convert(new short[]{1, 2}, int[].class, false, RuntimeContext.EMPTY));
        assertEquals(Arrays.asList(true, false),
                NodeMapper.convert(new boolean[]{true, false},
                        new TypeReference<List<Boolean>>() {}.getType(), true, RuntimeContext.EMPTY));
    }
}
