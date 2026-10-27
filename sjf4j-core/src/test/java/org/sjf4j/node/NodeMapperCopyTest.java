package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.TypeReference;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.NodeMapperFixtures.Box;
import org.sjf4j.node.NodeMapperFixtures.GenericUser;
import org.sjf4j.node.NodeMapperFixtures.Profile;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sjf4j.node.NodeMapperFixtures.profile;

@SuppressWarnings("unchecked")
class NodeMapperCopyTest {
    @Test
    void copyRecreatesNestedMutableNodesWhileConvertPreservesIdentity() {
        Profile source = profile();

        assertSame(source, NodeMapper.convert(source, Profile.class, false, RuntimeContext.EMPTY));

        Profile copied = (Profile) NodeMapper.convert(source, source.getClass(), true, RuntimeContext.EMPTY);

        assertNotSame(source, copied);
        assertNotSame(source.aliases, copied.aliases);
        assertNotSame(source.scores, copied.scores);
        assertNotSame(source.ranks, copied.ranks);
        assertNotSame(source.details, copied.details);

        copied.aliases.add("new");
        copied.details.getJsonObject("address").put("city", "Rome");
        copied.ranks[0] = 99;
        assertEquals(Arrays.asList("Ada", "A"), source.aliases);
        assertEquals("Oslo", source.details.getJsonObject("address").getString("city"));
        assertArrayEquals(new int[]{3, 5}, source.ranks);
    }

    @Test
    void convertDeepCopyControlsNestedContainerIdentity() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", "value");
        JsonObject nested = JsonObject.of("value", 1);
        map.put("nested", nested);
        JsonObject shallowObject = (JsonObject) NodeMapper.convert(map, JsonObject.class, false, RuntimeContext.EMPTY);
        JsonObject deepObject = (JsonObject) NodeMapper.convert(map, JsonObject.class, true, RuntimeContext.EMPTY);
        map.put("later", 2);

        assertFalse(shallowObject.containsKey("later"));
        assertFalse(deepObject.containsKey("later"));
        assertSame(nested, shallowObject.getJsonObject("nested"));
        assertNotSame(nested, deepObject.getJsonObject("nested"));

        List<Object> list = new ArrayList<>();
        list.add(1);
        JsonArray shallowArray = (JsonArray) NodeMapper.convert(list, JsonArray.class, false, RuntimeContext.EMPTY);
        JsonArray deepArray = (JsonArray) NodeMapper.convert(list, JsonArray.class, true, RuntimeContext.EMPTY);
        list.add(2);

        assertEquals(1, shallowArray.size());
        assertEquals(1, deepArray.size());
    }

    @Test
    void deepCopiesGenericPojoMembersFromParameterizedSource() {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("active", true);
        LinkedHashMap<String, Object> rawUser = new LinkedHashMap<>();
        rawUser.put("name", "A");
        rawUser.put("meta", meta);
        Box<Map<String, Object>> source = new Box<>();
        source.value = rawUser;

        Box<GenericUser> bound = (Box<GenericUser>) NodeMapper.convert(
                source, new TypeReference<Box<GenericUser>>() {}.getType(), true, RuntimeContext.EMPTY);

        assertNotSame(source, bound);
        assertInstanceOf(GenericUser.class, bound.value);
        assertEquals("A", bound.value.name);
        assertInstanceOf(Map.class, bound.value.meta);
        assertNotSame(meta, bound.value.meta);
    }

    @Test
    void typedContainerDeepCopyDoesNotAliasPojoMembers() {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("active", true);
        LinkedHashMap<String, Object> rawUser = new LinkedHashMap<>();
        rawUser.put("name", "A");
        rawUser.put("meta", meta);
        List<Map<String, Object>> source = new ArrayList<>();
        source.add(rawUser);

        List<GenericUser> deepCopied = (List<GenericUser>) NodeMapper.convert(
                source, new TypeReference<List<GenericUser>>() {}.getType(), true, RuntimeContext.EMPTY);

        assertNotSame(source, deepCopied);
        assertNotSame(meta, deepCopied.get(0).meta);
    }

    @Test
    void parameterizedContainersTraverseEvenForCompatibleShallowSources() {
        JsonObject nested = JsonObject.of("n", 1);
        List<Map<String, Object>> source = new ArrayList<>();
        source.add(new LinkedHashMap<>(Map.of("child", nested)));
        Type type = new TypeReference<List<Map<String, Object>>>() {}.getType();

        List<Map<String, Object>> shallow = (List<Map<String, Object>>) NodeMapper.convert(
                source, type, false, RuntimeContext.EMPTY);
        List<Map<String, Object>> deep = (List<Map<String, Object>>) NodeMapper.convert(
                source, type, true, RuntimeContext.EMPTY);

        assertNotSame(source, shallow);
        assertNotSame(source.get(0), shallow.get(0));
        assertSame(nested, shallow.get(0).get("child"));
        assertNotSame(nested, deep.get(0).get("child"));
        ((JsonObject) deep.get(0).get("child")).put("n", 2);
        assertEquals(1, nested.getInt("n"));
        assertSame(source, NodeMapper.convert(source, List.class, false, RuntimeContext.EMPTY));
    }

    @Test
    void deepCopyOfObjectAndGenericPojoPreservesRuntimeStructure() {
        Box<List<JsonObject>> source = new Box<>();
        source.value = new ArrayList<>(List.of(JsonObject.of("id", 1)));
        Type type = new TypeReference<Box<List<JsonObject>>>() {}.getType();

        Box<List<JsonObject>> shallow = (Box<List<JsonObject>>) NodeMapper.convert(
                source, type, false, RuntimeContext.EMPTY);
        Box<List<JsonObject>> deep = (Box<List<JsonObject>>) NodeMapper.convert(
                source, type, true, RuntimeContext.EMPTY);
        Object untyped = NodeMapper.convert(source, Object.class, true, RuntimeContext.EMPTY);

        assertNotSame(source, shallow);
        assertNotSame(source.value, shallow.value);
        assertSame(source.value.get(0), shallow.value.get(0));
        assertNotSame(source.value.get(0), deep.value.get(0));
        assertInstanceOf(Box.class, untyped);
        assertInstanceOf(List.class, ((Box<?>) untyped).value);
        assertNotSame(source.value.get(0), ((List<?>) ((Box<?>) untyped).value).get(0));
    }

    @Test
    void sequentialListDeepCopyAndRawConversionKeepOrderAndPaths() {
        JsonObject nested = JsonObject.of("value", 1);
        LinkedList<Object> source = new LinkedList<>();
        source.add(nested);
        source.add('A');

        List<Object> copied = (List<Object>) NodeMapper.convert(source, Object.class, true, RuntimeContext.EMPTY);
        assertNotSame(source, copied);
        assertNotSame(nested, copied.get(0));
        assertEquals('A', copied.get(1));

        List<Object> raw = (List<Object>) NodeMapper.convertToRaw(source, RuntimeContext.EMPTY);
        assertEquals(Arrays.asList(Map.of("value", 1), "A"), raw);
        assertNotSame(nested, raw.get(0));

        source.add(new Object());
        BindingException error = assertThrows(BindingException.class,
                () -> NodeMapper.convertToRaw(source, RuntimeContext.EMPTY));
        assertTrue(error.getMessage().contains("$[2]"));
    }
}
