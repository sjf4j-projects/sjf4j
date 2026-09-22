package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.ObjectPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Coverage for directional generic container resolution in compiled mapping. */
public class MapperGenericTest {

    @Test
    public void extendsWildcardsAreReadableSourcesForListSetAndMap() {
        GenericMapper mapper = CompiledInstances.of(GenericMapper.class);

        List<User> users = Arrays.asList(new User("Ada"), null, new User("Grace"));
        assertEquals(
                Arrays.asList(new UserDto("Ada"), null, new UserDto("Grace")),
                mapper.users(users));

        Set<User> set = new LinkedHashSet<>();
        set.add(new User("Ada"));
        set.add(null);
        set.add(new User("Grace"));
        assertEquals(
                Arrays.asList(new UserDto("Ada"), null, new UserDto("Grace")),
                new ArrayList<>(mapper.userSet(set)));

        Map<String, User> map = new LinkedHashMap<>();
        map.put("first", new User("Ada"));
        map.put("empty", null);
        map.put("second", new User("Grace"));

        Map<String, UserDto> result = mapper.userMap(map);
        assertEquals(List.of("first", "empty", "second"), new ArrayList<>(result.keySet()));
        assertEquals(new UserDto("Ada"), result.get("first"));
        assertNull(result.get("empty"));
        assertEquals(new UserDto("Grace"), result.get("second"));

        assertNull(mapper.users(null));
        assertNull(mapper.userSet(null));
        assertNull(mapper.userMap(null));
        assertEquals(List.of(), mapper.users(List.of()));
        assertEquals(Set.of(), mapper.userSet(Set.of()));
        assertEquals(Map.of(), mapper.userMap(Map.of()));

        List<UserDto> target = new ArrayList<>(List.of(new UserDto("old")));
        mapper.updateUsers(target, users);
        assertEquals(
                Arrays.asList(new UserDto("Ada"), null, new UserDto("Grace")),
                target);

        Map<String, UserDto> mapTarget = new LinkedHashMap<>();
        mapTarget.put("old", new UserDto("old"));
        mapper.updateUserMap(mapTarget, map);
        assertEquals(List.of("first", "empty", "second"), new ArrayList<>(mapTarget.keySet()));
        assertEquals(new UserDto("Ada"), mapTarget.get("first"));
        assertNull(mapTarget.get("empty"));
        assertEquals(new UserDto("Grace"), mapTarget.get("second"));
    }

    @Test
    public void nestedExtendsWildcardsResolveRecursively() {
        GenericMapper mapper = CompiledInstances.of(GenericMapper.class);

        List<User> first = Arrays.asList(new User("A"), null);
        List<User> second = List.of(new User("B"));
        List<List<User>> source = Arrays.asList(first, null, second);

        assertEquals(
                Arrays.asList(
                        Arrays.asList(new UserDto("A"), null),
                        null,
                        List.of(new UserDto("B"))),
                mapper.nestedUsers(source));

        Map<String, List<User>> grouped = new LinkedHashMap<>();
        grouped.put("a", first);
        grouped.put("none", null);
        grouped.put("b", second);

        Map<String, List<UserDto>> mapped = mapper.groupedUsers(grouped);
        assertEquals(Arrays.asList(new UserDto("A"), null), mapped.get("a"));
        assertNull(mapped.get("none"));
        assertEquals(List.of(new UserDto("B")), mapped.get("b"));
    }

    @Test
    public void unboundedWildcardsReadAsObject() {
        GenericMapper mapper = CompiledInstances.of(GenericMapper.class);

        List<?> list = Arrays.asList("text", 7, null);
        assertEquals(Arrays.asList("text", 7, null), mapper.objects(list));

        Set<?> set = new LinkedHashSet<>(Arrays.asList("text", 7, null));
        assertEquals(
                Arrays.asList("text", 7, null),
                new ArrayList<>(mapper.objectSet(set)));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("text", "value");
        values.put("number", 7);
        values.put("none", null);
        Map<String, ?> map = values;
        Map<String, Object> mapped = mapper.objectMap(map);
        assertEquals(List.of("text", "number", "none"), new ArrayList<>(mapped.keySet()));
        assertEquals("value", mapped.get("text"));
        assertEquals(7, mapped.get("number"));
        assertNull(mapped.get("none"));
    }


    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void rawContainersUseObjectAsReadableAndWritableFallback() {
        RawMapper mapper = CompiledInstances.of(RawMapper.class);

        List rawList = new ArrayList();
        rawList.add("text");
        rawList.add(7);
        rawList.add(null);
        assertEquals(Arrays.asList("text", 7, null), mapper.list(rawList));

        Set rawSet = new LinkedHashSet();
        rawSet.add("text");
        rawSet.add(7);
        rawSet.add(null);
        assertEquals(
                Arrays.asList("text", 7, null),
                new ArrayList<>(mapper.set(rawSet)));

        Map rawMap = new LinkedHashMap();
        rawMap.put("text", "value");
        rawMap.put(7, "number-key");
        rawMap.put("none", null);
        Map<Object, Object> mapped = mapper.map(rawMap);
        assertEquals(List.of("text", 7, "none"), new ArrayList<>(mapped.keySet()));
        assertEquals("value", mapped.get("text"));
        assertEquals("number-key", mapped.get(7));
        assertNull(mapped.get("none"));
    }

    @Test
    public void inheritedContainerTypeArgumentsAreResolvedForReadAndWrite() {
        GenericMapper mapper = CompiledInstances.of(GenericMapper.class);

        Users users = new Users();
        users.add(new User("Ada"));
        users.add(new User("Grace"));
        assertEquals(
                List.of(new UserDto("Ada"), new UserDto("Grace")),
                mapper.inheritedUsers(users));

        UsersByName byName = new UsersByName();
        byName.put("a", new User("Ada"));
        byName.put("g", new User("Grace"));
        assertEquals(
                Map.of("a", new UserDto("Ada"), "g", new UserDto("Grace")),
                mapper.inheritedMap(byName));
    }

    @Test
    public void inheritedGenericPojoPropertiesResolveBeforeNestedConversion() {
        GenericMapper mapper = CompiledInstances.of(GenericMapper.class);

        UserValueBox source = new UserValueBox();
        source.setValue(new User("Ada"));
        source.setValues(Arrays.asList(new User("Grace"), null));

        UserDtoValueBox target = mapper.inheritedBox(source);
        assertEquals(new UserDto("Ada"), target.value);
        assertEquals(Arrays.asList(new UserDto("Grace"), null), target.values);
    }

    @Test
    public void wildcardTypedPropertiesMapIntoConcreteWritableProperties() {
        GenericMapper mapper = CompiledInstances.of(GenericMapper.class);

        List<User> users = Arrays.asList(new User("Ada"), null);
        Set<User> set = new LinkedHashSet<>();
        set.add(new User("Grace"));
        Map<String, User> map = new LinkedHashMap<>();
        map.put("primary", new User("Katherine"));

        WildcardBox source = new WildcardBox();
        source.users = users;
        source.userSet = set;
        source.userMap = map;

        DtoBox target = mapper.box(source);
        assertEquals(Arrays.asList(new UserDto("Ada"), null), target.users);
        assertEquals(List.of(new UserDto("Grace")), new ArrayList<>(target.userSet));
        assertEquals(new UserDto("Katherine"), target.userMap.get("primary"));
    }

    @Test
    public void assignableWildcardTargetsCanRemainDirectWithoutContainerWrites() {
        GenericMapper mapper = CompiledInstances.of(GenericMapper.class);

        List<User> users = new ArrayList<>(List.of(new User("Ada")));
        Map<String, User> map = new LinkedHashMap<>();
        map.put("primary", users.get(0));

        assertSame(users, mapper.covariantList(users));
        assertSame(map, mapper.covariantMap(map));
    }

    public record User(String name) {
    }

    public record UserDto(String name) {
    }

    public static final class Users extends ArrayList<User> {
    }

    public static final class UsersByName extends LinkedHashMap<String, User> {
    }

    public static class GenericValueBox<T> {
        private T value;
        private List<T> values;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public List<T> getValues() {
            return values;
        }

        public void setValues(List<T> values) {
            this.values = values;
        }
    }

    public static final class UserValueBox extends GenericValueBox<User> {
    }

    public static final class UserDtoValueBox {
        public UserDto value;
        public List<UserDto> values;
    }

    public static final class WildcardBox {
        public List<? extends User> users;
        public Set<? extends User> userSet;
        public Map<? extends String, ? extends User> userMap;
    }

    public static final class DtoBox {
        public List<UserDto> users;
        public Set<UserDto> userSet;
        public Map<String, UserDto> userMap;
    }

    @CompiledMapper
    public interface GenericMapper {
        @MappingOptions(using = {"toDto"})
        List<UserDto> users(List<? extends User> source);

        @MappingOptions(using = {"toDto"})
        Set<UserDto> userSet(Set<? extends User> source);

        @MappingOptions(using = {"toDto"})
        void updateUsers(List<UserDto> target, List<? extends User> source);

        @MappingOptions(using = {"toDto"})
        Map<String, UserDto> userMap(Map<? extends String, ? extends User> source);

        @MappingOptions(objects = ObjectPolicy.CLEAR_PUT, using = {"toDto"})
        void updateUserMap(
                Map<String, UserDto> target,
                Map<? extends String, ? extends User> source);

        @MappingOptions(using = {"toDto"})
        List<List<UserDto>> nestedUsers(List<? extends List<? extends User>> source);

        @MappingOptions(using = {"toDto"})
        Map<String, List<UserDto>> groupedUsers(
                Map<? extends String, ? extends List<? extends User>> source);

        List<Object> objects(List<?> source);

        Set<Object> objectSet(Set<?> source);

        Map<String, Object> objectMap(Map<String, ?> source);

        @MappingOptions(using = {"toDto"})
        List<UserDto> inheritedUsers(Users source);

        @MappingOptions(using = {"toDto"})
        Map<String, UserDto> inheritedMap(UsersByName source);

        @MappingOptions(using = {"toDto"})
        UserDtoValueBox inheritedBox(UserValueBox source);

        @MappingOptions(using = {"toDto"})
        DtoBox box(WildcardBox source);

        List<? extends User> covariantList(List<User> source);

        Map<String, ? extends User> covariantMap(Map<String, User> source);

        UserDto toDto(User source);
    }
    @CompiledMapper
    @SuppressWarnings("rawtypes")
    public interface RawMapper {
        List<Object> list(List source);

        Set<Object> set(Set source);

        Map<Object, Object> map(Map source);
    }

}
