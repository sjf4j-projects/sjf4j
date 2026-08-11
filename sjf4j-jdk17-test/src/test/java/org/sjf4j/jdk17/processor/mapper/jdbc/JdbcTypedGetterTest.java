package org.sjf4j.jdk17.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.BindingException;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.result;

class JdbcTypedGetterTest {
    @Test
    void usesTypedGetterForPrimitivePropertiesAndRejectsNull() {
        Mapper mapper = CompiledNodes.of(Mapper.class);
        int[] getInts = {0};

        assertEquals(36, mapper.user(typedUserResult(getInts, false)).age);
        assertEquals(1, getInts[0]);

        BindingException nullValue = assertThrows(BindingException.class,
                () -> mapper.user(typedUserResult(getInts, true)));
        assertEquals("SQL NULL for column 'age' mapped to primitive int", nullValue.getMessage());
        assertEquals(null, mapper.boxedAge(result(new String[]{"age"}, new Object[]{null})).age());
    }

    @Test
    void usesAllTypedGettersAndChecksWasNullForPrimitiveColumns() {
        Mapper mapper = CompiledNodes.of(Mapper.class);
        List<String> getters = new ArrayList<>();

        PrimitiveValues values = mapper.primitives(typedPrimitiveResult(getters, null, false));
        assertEquals("Ada", values.text);
        assertEquals(7L, values.longValue);
        assertEquals((short) 3, values.shortValue);
        assertEquals((byte) 2, values.byteValue);
        assertEquals(1.5d, values.doubleValue);
        assertEquals(2.5f, values.floatValue);
        assertEquals(true, values.booleanValue);
        assertEquals(Arrays.asList("getString", "getLong", "getShort", "getByte", "getDouble",
                "getFloat", "getBoolean"), getters);

        BindingException nullValue = assertThrows(BindingException.class,
                () -> mapper.primitives(typedPrimitiveResult(new ArrayList<>(), "longValue", false)));
        assertEquals("SQL NULL for column 'longValue' mapped to primitive long", nullValue.getMessage());

        getters.clear();
        assertEquals(1, mapper.primitiveValues(typedPrimitiveResult(getters, null, true)).size());
        assertEquals(Arrays.asList("getString#", "getLong#", "getShort#", "getByte#", "getDouble#",
                "getFloat#", "getBoolean#"), getters);
    }

    @CompiledJdbcMapper
    interface Mapper {
        @Mapping(target = "name", source = "full_name")
        User user(ResultSet rs);

        BoxedAge boxedAge(ResultSet rs);

        PrimitiveValues primitives(ResultSet rs);

        List<PrimitiveValues> primitiveValues(ResultSet rs);
    }

    public static final class User {
        public String name;
        public int age;

        public User() {
        }
    }

    public record BoxedAge(Integer age) {
    }

    public static final class PrimitiveValues {
        public String text;
        public long longValue;
        public short shortValue;
        public byte byteValue;
        public double doubleValue;
        public float floatValue;
        public boolean booleanValue;

        public PrimitiveValues() {
        }
    }

    private static ResultSet typedUserResult(int[] getInts, boolean nullAge) {
        int[] row = {-1};
        return (ResultSet) Proxy.newProxyInstance(JdbcTypedGetterTest.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        return ++row[0] == 0;
                    }
                    if (method.getName().equals("getString")) {
                        assertEquals("full_name", arguments[0]);
                        return "Ada";
                    }
                    if (method.getName().equals("getObject")) {
                        return null;
                    }
                    if (method.getName().equals("getInt")) {
                        assertEquals("age", arguments[0]);
                        getInts[0]++;
                        return nullAge ? 0 : 36;
                    }
                    if (method.getName().equals("wasNull")) {
                        return nullAge;
                    }
                    return null;
                });
    }

    private static ResultSet typedPrimitiveResult(List<String> getters, String nullColumn, boolean indexed) {
        String[] columns = {"text", "longValue", "shortValue", "byteValue", "doubleValue", "floatValue", "booleanValue"};
        Object[] values = {"Ada", 7L, (short) 3, (byte) 2, 1.5d, 2.5f, true};
        int[] row = {-1};
        Object[] last = {null};
        return (ResultSet) Proxy.newProxyInstance(JdbcTypedGetterTest.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        return ++row[0] == 0;
                    }
                    if (method.getName().equals("findColumn")) {
                        return List.of(columns).indexOf(arguments[0]) + 1;
                    }
                    if (method.getName().equals("getObject")) {
                        throw new AssertionError("typed value read through getObject");
                    }
                    if (method.getName().equals("wasNull")) {
                        return last[0] == null;
                    }
                    if (!method.getName().startsWith("get")) {
                        return null;
                    }

                    int column = arguments[0] instanceof Integer
                            ? (Integer) arguments[0]
                            : List.of(columns).indexOf(arguments[0]) + 1;
                    getters.add(method.getName() + (arguments[0] instanceof Integer ? "#" : ""));
                    last[0] = columns[column - 1].equals(nullColumn) ? null : values[column - 1];
                    if (method.getName().equals("getString") || last[0] != null) {
                        return last[0];
                    }
                    if (method.getName().equals("getLong")) {
                        return 0L;
                    }
                    if (method.getName().equals("getShort")) {
                        return (short) 0;
                    }
                    if (method.getName().equals("getByte")) {
                        return (byte) 0;
                    }
                    if (method.getName().equals("getDouble")) {
                        return 0d;
                    }
                    if (method.getName().equals("getFloat")) {
                        return 0f;
                    }
                    if (method.getName().equals("getBoolean")) {
                        return false;
                    }
                    return 0;
                });
    }
}
