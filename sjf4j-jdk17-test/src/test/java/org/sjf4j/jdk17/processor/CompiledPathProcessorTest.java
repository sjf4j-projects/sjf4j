package org.sjf4j.jdk17.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.compiled.CompiledNodes;
import org.sjf4j.annotation.compiled.GetByPath;
import org.sjf4j.annotation.compiled.PutByPath;
import org.sjf4j.compiled.CompiledNodesRegistry;
import org.sjf4j.exception.JsonException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CompiledPathProcessorTest {

    @Test
    public void getFromRecordPath() {
        UserNodes nodes = CompiledNodesRegistry.of(UserNodes.class);

        assertEquals("Hangzhou", nodes.getCityName(new User(new City("Hangzhou"))));
        assertNull(nodes.getCityName(new User(null)));
        assertNull(nodes.getCityName(null));
    }

    @Test
    public void getFromDynamicAndIndexedPath() {
        UserNodes nodes = CompiledNodesRegistry.of(UserNodes.class);

        assertEquals("Han", nodes.getObjectName(Map.of("payload", Map.of("user", Map.of("name", "Han")))));
        assertEquals(Integer.valueOf(3), nodes.getLast(List.of(1, 2, 3)));
        assertEquals(Integer.valueOf(3), nodes.getLastArray(new Integer[]{1, 2, 3}));
        assertEquals("Hangzhou", nodes.getJsonName(JsonObject.of("child", JsonObject.of("name", "Hangzhou"))));
        assertNull(nodes.getLast(List.of()));
    }

    @Test
    public void getFromDirectEmitBranches() {
        UserNodes nodes = CompiledNodesRegistry.of(UserNodes.class);

        assertEquals("typed", nodes.getTypedMapName(Map.of("name", "typed")));
        assertEquals("Hangzhou", nodes.getTypedUserMapCityName(Map.of("user", new User(new City("Hangzhou")))));
        assertEquals("array", nodes.getJsonArrayValue(JsonArray.of("array")));
        assertEquals("field", nodes.getPublicField(new FieldBean("field")));
        assertEquals(Boolean.TRUE, nodes.isActive(new FlagBean(true)));
        assertEquals(3, nodes.getPrimitiveLast(List.of(1, 2, 3)));
        assertEquals(Integer.valueOf(123), nodes.getIntegerValue(Map.of("value", 123)));
        assertThrows(JsonException.class, () -> nodes.getPrimitiveLast(List.of()));
        assertThrows(JsonException.class, () -> nodes.getPrimitiveLast(null));
    }

    @Test
    public void putMutatesMapListJsonAndPojo() {
        UserNodes nodes = CompiledNodesRegistry.of(UserNodes.class);

        Map<String, String> map = new HashMap<>();
        map.put("name", "old");
        assertEquals("old", nodes.putMapName(map, "new"));
        assertEquals("new", map.get("name"));

        List<Integer> list = new ArrayList<>(List.of(1, 2, 3));
        assertEquals(Integer.valueOf(3), nodes.putLast(list, 9));
        assertEquals(List.of(1, 2, 9), list);
        assertNull(nodes.putAppend(list, 10));
        assertEquals(List.of(1, 2, 9, 10), list);
        assertNull(nodes.putAtSize(list, 11));
        assertEquals(List.of(1, 2, 9, 10, 11), list);
        nodes.putAtSize2(list, 111);

        JsonObject json = JsonObject.of("name", "json-old");
        assertEquals("json-old", nodes.putJsonName(json, "json-new"));
        assertEquals("json-new", json.getString("name"));

        JsonArray jsonArray = JsonArray.of("json-array-old");
        assertEquals("json-array-old", nodes.putJsonArrayValue(jsonArray, "json-array-new"));
        assertEquals("json-array-new", jsonArray.getString(0));
        assertNull(nodes.putJsonArrayAppend(jsonArray, "json-array-appended"));
        assertEquals("json-array-appended", jsonArray.getString(1));
        nodes.putJsonArrayAppend2(jsonArray, "json-array-appended");

        Integer[] array = {1, 2, 3};
        assertEquals(Integer.valueOf(3), nodes.putLastArray(array, 7));
        assertEquals(Integer.valueOf(7), array[2]);

        MutableBean bean = new MutableBean("bean-old", "field-old");
        assertEquals("bean-old", nodes.putBeanValue(bean, "bean-new"));
        assertEquals("bean-new", bean.getValue());
        nodes.putBeanField(bean, "field-new");
        assertEquals("field-new", bean.field);
    }

    @Test
    public void putSupportsConversionAndPrimitiveMissing() {
        UserNodes nodes = CompiledNodesRegistry.of(UserNodes.class);

        Map<String, Long> map = new HashMap<>();
        map.put("value", 1L);
        assertEquals(Long.valueOf(1), nodes.putLongValue(map, 2L));
        assertEquals(Long.valueOf(2), map.get("value"));

        Map<String, Integer> typed = new HashMap<>();
        typed.put("value", 1);
        assertEquals(1, nodes.putPrimitiveOld(typed, 2));
        assertEquals(2, typed.get("value"));
    }

    record User(City city) {}

    record City(String name) {}

    static final class FieldBean {
        public final String value;

        FieldBean(String value) { this.value = value; }
    }

    static final class FlagBean {
        private final boolean active;

        FlagBean(boolean active) { this.active = active; }

        public boolean isActive() { return active; }
    }

    static final class MutableBean {
        private String value;
        public String field;

        MutableBean(String value, String field) {
            this.value = value;
            this.field = field;
        }

        public String getValue() { return value; }

        public void setValue(String value) { this.value = value; }
    }

    @CompiledNodes
    interface UserNodes {
        @GetByPath("$.city.name")
        String getCityName(CompiledPathProcessorTest.User user);

        @GetByPath("$.payload.user.name")
        Object getObjectName(Object root);

        @GetByPath("$[-1]")
        Integer getLast(List<Integer> values);

        @GetByPath("$[-1]")
        Integer getLastArray(Integer[] values);

        @GetByPath("$.child.name")
        Object getJsonName(JsonObject root);

        @GetByPath("$.name")
        String getTypedMapName(Map<String, String> root);

        @GetByPath("$.user.city.name")
        String getTypedUserMapCityName(Map<String, User> root);

        @GetByPath("$[0]")
        Object getJsonArrayValue(JsonArray root);

        @GetByPath("$.value")
        String getPublicField(FieldBean root);

        @GetByPath("$.active")
        Boolean isActive(FlagBean root);

        @GetByPath("$[-1]")
        int getPrimitiveLast(List<Integer> values);

        @GetByPath("$.value")
        Integer getIntegerValue(Map<String, Integer> root);

        @PutByPath("$.name")
        String putMapName(Map<String, String> root, String value);

        @PutByPath("$[-1]")
        Integer putLast(List<Integer> root, Integer value);

        @PutByPath("$[+]")
        Integer putAppend(List<Integer> root, Integer value);

        @PutByPath("$[4]")
        Integer putAtSize(List<Integer> root, Integer value);

        @PutByPath("$[4]")
        void putAtSize2(List<Integer> root, int value);

        @PutByPath("$.name")
        Object putJsonName(JsonObject root, String value);

        @PutByPath("$[0]")
        Object putJsonArrayValue(JsonArray root, String value);

        @PutByPath("$[+]")
        Object putJsonArrayAppend(JsonArray root, String value);

        @PutByPath("$[+]")
        void putJsonArrayAppend2(JsonArray root, String value);

        @PutByPath("$[-1]")
        Integer putLastArray(Integer[] root, Integer value);

        @PutByPath("$.value")
        String putBeanValue(MutableBean root, String value);

        @PutByPath("$.field")
        void putBeanField(MutableBean root, String value);

        @PutByPath("$.value")
        Long putLongValue(Map<String, Long> root, Long value);

        @PutByPath("$.value")
        int putPrimitiveOld(Map<String, Integer> root, int value);

        static void printMe() {
            System.out.println("haha");
        }
    }

}
