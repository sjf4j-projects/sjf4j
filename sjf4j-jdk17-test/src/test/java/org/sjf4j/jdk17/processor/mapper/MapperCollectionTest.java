package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.ArrayPolicy;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MapperOptions;
import org.sjf4j.annotation.mapper.NullValuePolicy;
import org.sjf4j.annotation.mapper.ObjectPolicy;
import org.sjf4j.compiled.CompiledNodes;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class MapperCollectionTest {
    @Test public void rootListDirectAndMapped() {
        CollectionMapper m = CompiledNodes.of(CollectionMapper.class);
        assertEquals(List.of("a", "b"), m.strings(List.of("a", "b")));
        assertEquals(List.of(new UserDto("a"), new UserDto("b")), CompiledNodes.of(UniqueMapper.class).users(List.of(new User("a"), new User("b"))));
        assertNull(m.strings(null));
    }

    @Test public void withSelectsConverterAndSetKeepsOrder() {
        CollectionMapper m = CompiledNodes.of(CollectionMapper.class);
        assertEquals(List.of(new UserDto("x!")), m.usersWith(List.of(new User("x"))));
        Set<String> out = m.set(new LinkedHashSet<>(List.of("b", "a")));
        assertEquals(List.of("b", "a"), new ArrayList<>(out));
    }

    @Test public void rootMapCreateAndUpdates() {
        CollectionMapper m = CompiledNodes.of(CollectionMapper.class);
        Map<String, User> in = new LinkedHashMap<>();
        in.put("a", new User("A"));
        assertEquals(new UserDto("A"), CompiledNodes.of(UniqueMapper.class).map(in).get("a"));

        List<List<User>> nested = new ArrayList<>();
        nested.add(Arrays.asList(new User("A"), null));
        nested.add(null);
        nested.add(List.of(new User("B")));
        assertEquals(Arrays.asList(Arrays.asList(new UserDto("A"), null), null, Arrays.asList(new UserDto("B"))), m.nestedUsers(nested));

        Map<String, List<User>> grouped = new LinkedHashMap<>();
        grouped.put("a", Arrays.asList(new User("A"), null));
        grouped.put("b", null);
        Map<String, List<UserDto>> groupedDto = m.groupedUsers(grouped);
        assertEquals(Arrays.asList(new UserDto("A"), null), groupedDto.get("a"));
        assertNull(groupedDto.get("b"));

        Map<String, Map<String, User>> nestedMap = new LinkedHashMap<>();
        nestedMap.put("outer", Map.of("inner", new User("C")));
        assertEquals(new UserDto("C"), m.nestedMapUsers(nestedMap).get("outer").get("inner"));

        List<Map<String, User>> mapList = List.of(Map.of("x", new User("X")));
        assertEquals(new UserDto("X"), m.userMaps(mapList).get(0).get("x"));

        List<String> list = new ArrayList<>(List.of("old"));
        m.replace(list, List.of("n"));
        assertEquals(List.of("n"), list);
        m.append(list, List.of("x"));
        assertEquals(List.of("n", "x"), list);

        Map<String, String> target = new LinkedHashMap<>();
        target.put("old", "1");
        m.replaceMap(target, Map.of("n", "2"));
        assertEquals(Set.of("n"), target.keySet());
        m.appendMap(target, Map.of("x", "3"));
        assertEquals("3", target.get("x"));
        m.putIfAbsentMap(target, Map.of("x", "4", "y", "5"));
        assertEquals("3", target.get("x"));
        assertEquals("5", target.get("y"));
    }

    @Test public void autoNestedRecursivelyUsesUniqueLeafConverter() {
        AutoNestedMapper m = CompiledNodes.of(AutoNestedMapper.class);

        List<List<User>> nested = new ArrayList<>();
        nested.add(Arrays.asList(new User("A"), null));
        nested.add(null);
        nested.add(List.of(new User("B")));
        assertEquals(Arrays.asList(Arrays.asList(new UserDto("A"), null), null, Arrays.asList(new UserDto("B"))), m.nestedUsers(nested));

        Map<String, List<User>> grouped = new LinkedHashMap<>();
        grouped.put("a", Arrays.asList(new User("A"), null));
        grouped.put("b", null);
        Map<String, List<UserDto>> groupedDto = m.groupedUsers(grouped);
        assertEquals(Arrays.asList(new UserDto("A"), null), groupedDto.get("a"));
        assertNull(groupedDto.get("b"));

        Map<String, Map<String, User>> nestedMap = new LinkedHashMap<>();
        nestedMap.put("outer", Map.of("inner", new User("C")));
        assertEquals(new UserDto("C"), m.nestedMapUsers(nestedMap).get("outer").get("inner"));
    }

    @Test public void beanCreateAndUpdateContainers() {
        CollectionMapper m = CompiledNodes.of(CollectionMapper.class);
        UserBox box = new UserBox();
        box.users = List.of(new User("a"));
        box.map = Map.of("b", new User("b"));
        box.nestedUsers = new ArrayList<>();
        box.nestedUsers.add(Arrays.asList(new User("n1"), null));
        box.nestedUsers.add(null);
        box.groupedUsers = new LinkedHashMap<>();
        box.groupedUsers.put("g1", Arrays.asList(new User("g1"), null));
        box.groupedUsers.put("g2", null);
        DtoBox dto = m.box(box);
        assertEquals(new UserDto("a"), dto.users.get(0));
        assertEquals(new UserDto("b"), dto.map.get("b"));
        assertEquals(Arrays.asList(new UserDto("n1"), null), dto.nestedUsers.get(0));
        assertNull(dto.nestedUsers.get(1));
        assertEquals(Arrays.asList(new UserDto("g1"), null), dto.groupedUsers.get("g1"));
        assertNull(dto.groupedUsers.get("g2"));

        DtoBox target = new DtoBox();
        target.users = new ArrayList<>(List.of(new UserDto("old")));
        target.map = new LinkedHashMap<>();
        target.map.put("keep", new UserDto("keep"));
        target.nestedUsers = new ArrayList<>(List.of(List.of(new UserDto("old"))));
        target.groupedUsers = new LinkedHashMap<>();
        target.groupedUsers.put("old", List.of(new UserDto("old")));
        m.updateBox(target, box);
        assertEquals(List.of(new UserDto("a")), target.users);
        assertEquals(Map.of("keep", new UserDto("keep")), target.map);
        assertEquals(Arrays.asList(new UserDto("n1"), null), target.nestedUsers.get(0));
        assertNull(target.nestedUsers.get(1));
        assertEquals(Arrays.asList(new UserDto("g1"), null), target.groupedUsers.get("g1"));
        assertNull(target.groupedUsers.get("g2"));
        m.ignoreNullBox(target, new UserBox());
        assertEquals(List.of(new UserDto("a")), target.users);
        assertEquals(Map.of("keep", new UserDto("keep")), target.map);
        assertEquals(Arrays.asList(new UserDto("n1"), null), target.nestedUsers.get(0));
        assertNull(target.nestedUsers.get(1));
        assertEquals(Arrays.asList(new UserDto("g1"), null), target.groupedUsers.get("g1"));
        assertNull(target.groupedUsers.get("g2"));
        m.setNullBox(target, new UserBox());
        assertNull(target.users);
        assertNull(target.nestedUsers);
        assertNull(target.groupedUsers);
        assertEquals(Map.of("keep", new UserDto("keep")), target.map);
    }

    @Test public void beanAppendField() {
        CollectionMapper m = CompiledNodes.of(CollectionMapper.class);
        DtoBox target = new DtoBox();
        target.users = new ArrayList<>(List.of(new UserDto("old")));
        UserBox source = new UserBox();
        source.users = List.of(new User("a"));
        m.appendBox(target, source);
        assertEquals(List.of(new UserDto("old"), new UserDto("a")), target.users);
    }

    @Test public void beanMapObjectPolicies() {
        CollectionMapper m = CompiledNodes.of(CollectionMapper.class);
        UserBox source = new UserBox();
        source.map = new LinkedHashMap<>();
        source.map.put("old", new User("new-old"));
        source.map.put("b", new User("b"));

        DtoBox target = new DtoBox();
        target.map = new LinkedHashMap<>();
        target.map.put("old", new UserDto("old"));
        m.putBox(target, source);
        assertEquals(new UserDto("new-old"), target.map.get("old"));
        assertEquals(new UserDto("b"), target.map.get("b"));

        target.map.put("keep", new UserDto("keep"));
        m.clearPutBox(target, source);
        assertFalse(target.map.containsKey("keep"));
        assertEquals(Set.of("old", "b"), target.map.keySet());

        target.map.put("old", new UserDto("existing"));
        target.map.put("empty", null);
        target.map.remove("b");
        source.map.put("empty", new User("filled"));
        m.putIfAbsentBox(target, source);
        assertEquals(new UserDto("existing"), target.map.get("old"));
        assertEquals(new UserDto("b"), target.map.get("b"));
        assertEquals(new UserDto("filled"), target.map.get("empty"));

        DtoBox nullTarget = new DtoBox();
        nullTarget.map = new LinkedHashMap<>();
        nullTarget.map.put("keep", new UserDto("keep"));
        m.setNullMapBox(nullTarget, new UserBox());
        assertNull(nullTarget.map);
    }

    @Test public void putIfAbsentMapSkipsConverterForExistingValue() {
        PutIfAbsentMapper.calls[0] = 0;
        PutIfAbsentMapper m = CompiledNodes.of(PutIfAbsentMapper.class);
        Map<String, String> target = new LinkedHashMap<>();
        target.put("keep", "old");
        target.put("fill", null);
        Map<String, String> source = new LinkedHashMap<>();
        source.put("keep", "boom");
        source.put("fill", "filled");
        source.put("add", "added");

        m.update(target, source);

        assertEquals("old", target.get("keep"));
        assertEquals("FILLED", target.get("fill"));
        assertEquals("ADDED", target.get("add"));
        assertEquals(2, PutIfAbsentMapper.calls[0]);
    }

    @Test public void recursiveUpdatePoliciesApplyToNestedContainers() {
        RecursiveUpdateMapper m = CompiledNodes.of(RecursiveUpdateMapper.class);

        Map<String, List<UserDto>> listTarget = new LinkedHashMap<>();
        List<UserDto> existingList = new ArrayList<>(List.of(new UserDto("old")));
        listTarget.put("keep", existingList);
        Map<String, List<User>> listSource = new LinkedHashMap<>();
        listSource.put("keep", List.of(new User("a"), new User("b")));
        listSource.put("add", List.of(new User("c")));
        m.putClearAddLists(listTarget, listSource);
        assertSame(existingList, listTarget.get("keep"));
        assertEquals(List.of(new UserDto("a"), new UserDto("b")), listTarget.get("keep"));
        assertEquals(List.of(new UserDto("c")), listTarget.get("add"));

        Map<String, List<UserDto>> absentTarget = new LinkedHashMap<>();
        List<UserDto> absentExisting = new ArrayList<>(List.of(new UserDto("keep")));
        absentTarget.put("keep", absentExisting);
        absentTarget.put("null", null);
        Map<String, List<User>> absentSource = new LinkedHashMap<>();
        absentSource.put("keep", List.of(new User("ignored")));
        absentSource.put("null", List.of(new User("filled")));
        absentSource.put("add", List.of(new User("new")));
        m.putIfAbsentLists(absentTarget, absentSource);
        assertSame(absentExisting, absentTarget.get("keep"));
        assertEquals(List.of(new UserDto("keep")), absentTarget.get("keep"));
        assertEquals(List.of(new UserDto("filled")), absentTarget.get("null"));
        assertEquals(List.of(new UserDto("new")), absentTarget.get("add"));

        Map<String, List<UserDto>> appendTarget = new LinkedHashMap<>();
        List<UserDto> appendExisting = new ArrayList<>(List.of(new UserDto("old")));
        appendTarget.put("keep", appendExisting);
        Map<String, List<User>> appendSource = new LinkedHashMap<>();
        appendSource.put("keep", List.of(new User("x"), new User("y")));
        m.putAddLists(appendTarget, appendSource);
        assertSame(appendExisting, appendTarget.get("keep"));
        assertEquals(List.of(new UserDto("old"), new UserDto("x"), new UserDto("y")), appendTarget.get("keep"));

        Map<String, Map<String, UserDto>> mapTarget = new LinkedHashMap<>();
        Map<String, UserDto> existingInner = new LinkedHashMap<>();
        existingInner.put("keep", new UserDto("keep"));
        existingInner.put("old", new UserDto("old"));
        mapTarget.put("outer", existingInner);
        Map<String, Map<String, User>> mapSource = new LinkedHashMap<>();
        Map<String, User> sourceInner = new LinkedHashMap<>();
        sourceInner.put("old", new User("new-old"));
        sourceInner.put("add", new User("add"));
        mapSource.put("outer", sourceInner);
        m.putMaps(mapTarget, mapSource);
        assertSame(existingInner, mapTarget.get("outer"));
        assertEquals(new UserDto("keep"), mapTarget.get("outer").get("keep"));
        assertEquals(new UserDto("new-old"), mapTarget.get("outer").get("old"));
        assertEquals(new UserDto("add"), mapTarget.get("outer").get("add"));

        Map<String, Map<String, List<UserDto>>> deepTarget = new LinkedHashMap<>();
        Map<String, List<UserDto>> deepInner = new LinkedHashMap<>();
        List<UserDto> deepList = new ArrayList<>(List.of(new UserDto("old")));
        deepInner.put("list", deepList);
        deepTarget.put("outer", deepInner);
        Map<String, Map<String, List<User>>> deepSource = new LinkedHashMap<>();
        Map<String, List<User>> deepSourceInner = new LinkedHashMap<>();
        deepSourceInner.put("list", List.of(new User("new")));
        deepSourceInner.put("add", List.of(new User("added")));
        deepSource.put("outer", deepSourceInner);
        m.putDeep(deepTarget, deepSource);
        assertSame(deepInner, deepTarget.get("outer"));
        assertSame(deepList, deepTarget.get("outer").get("list"));
        assertEquals(List.of(new UserDto("old"), new UserDto("new")), deepTarget.get("outer").get("list"));
        assertEquals(List.of(new UserDto("added")), deepTarget.get("outer").get("add"));

        Map<String, Map<String, UserDto>> clearTarget = new LinkedHashMap<>();
        Map<String, UserDto> oldInner = new LinkedHashMap<>();
        oldInner.put("gone", new UserDto("gone"));
        clearTarget.put("old", oldInner);
        Map<String, Map<String, User>> clearSource = new LinkedHashMap<>();
        clearSource.put("outer", Map.of("new", new User("new")));
        m.clearPutMaps(clearTarget, clearSource);
        assertEquals(Set.of("outer"), clearTarget.keySet());
        assertNotSame(oldInner, clearTarget.get("outer"));
        assertEquals(new UserDto("new"), clearTarget.get("outer").get("new"));
    }

    @Test public void mapsObntStructuralKinds() {
        CollectionMapper m = CompiledNodes.of(CollectionMapper.class);
        ObntSource source = new ObntSource();
        source.text = "text";
        source.integer = 7;
        source.number = 1.5D;
        source.bool = true;
        source.nil = null;
        source.array = Arrays.asList("a", null, "b");
        source.set = new LinkedHashSet<>(Arrays.asList(2, 1));
        source.object = new LinkedHashMap<>();
        source.object.put("yes", true);
        source.object.put("none", null);
        source.users = Arrays.asList(new User("ada"), null);
        source.userMap = new LinkedHashMap<>();
        source.userMap.put("u", new User("grace"));
        source.child = new Child("kid");

        ObntTarget created = m.obnt(source);
        assertEquals("text", created.text);
        assertEquals(7, created.integer);
        assertEquals(1.5D, created.number);
        assertEquals(true, created.bool);
        assertNull(created.nil);
        assertEquals(Arrays.asList("a", null, "b"), created.array);
        assertEquals(Arrays.asList(2, 1), new ArrayList<>(created.set));
        assertEquals(Boolean.TRUE, created.object.get("yes"));
        assertTrue(created.object.containsKey("none"));
        assertNull(created.object.get("none"));
        assertEquals(Arrays.asList(new UserDto("ada"), null), created.users);
        assertEquals(new UserDto("grace"), created.userMap.get("u"));
        assertEquals(new ChildDto("kid"), created.child);

        ObntTarget target = new ObntTarget();
        target.array = new ArrayList<>(List.of("old"));
        target.object = new LinkedHashMap<>();
        target.object.put("keep", false);
        target.object.put("yes", false);
        target.users = new ArrayList<>(List.of(new UserDto("old")));
        target.userMap = new LinkedHashMap<>();
        target.userMap.put("old", new UserDto("old"));
        m.updateObnt(target, source);
        assertEquals(Arrays.asList("a", null, "b"), target.array);
        assertEquals(Boolean.FALSE, target.object.get("keep"));
        assertEquals(Boolean.TRUE, target.object.get("yes"));
        assertEquals(Arrays.asList(new UserDto("ada"), null), target.users);
        assertEquals(new UserDto("old"), target.userMap.get("old"));
        assertEquals(new UserDto("grace"), target.userMap.get("u"));
        assertEquals(new ChildDto("kid"), target.child);
    }

    public record User(String name) {}
    public record UserDto(String name) {}
    public record Child(String name) {}
    public record ChildDto(String name) {}
    public static class UserBox { public List<User> users; public Map<String, User> map; public List<List<User>> nestedUsers; public Map<String, List<User>> groupedUsers; }
    public static class DtoBox { public List<UserDto> users; public Map<String, UserDto> map; public List<List<UserDto>> nestedUsers; public Map<String, List<UserDto>> groupedUsers; }
    public static class ObntSource {
        public String text;
        public Integer integer;
        public Double number;
        public Boolean bool;
        public Object nil;
        public List<String> array;
        public Set<Integer> set;
        public Map<String, Boolean> object;
        public List<User> users;
        public Map<String, User> userMap;
        public Child child;
    }
    public static class ObntTarget {
        public String text;
        public Integer integer;
        public Double number;
        public Boolean bool;
        public Object nil;
        public List<String> array;
        public Set<Integer> set;
        public Map<String, Boolean> object;
        public List<UserDto> users;
        public Map<String, UserDto> userMap;
        public ChildDto child;
    }

    @CompiledMapper
    public interface CollectionMapper {
        List<String> strings(List<String> in);
        @Mapping(nestedMapper = "toDto") List<UserDto> users(List<User> in);
        @Mapping(nestedMapper = "special") List<UserDto> usersWith(List<User> in);
        Set<String> set(Set<String> in);
        @Mapping(nestedMapper = "toDto") Map<String, UserDto> map(Map<String, User> in);
        @Mapping(nestedMapper = "toDto") List<List<UserDto>> nestedUsers(List<List<User>> in);
        @Mapping(nestedMapper = "toDto") Map<String, List<UserDto>> groupedUsers(Map<String, List<User>> in);
        @Mapping(nestedMapper = "toDto") Map<String, Map<String, UserDto>> nestedMapUsers(Map<String, Map<String, User>> in);
        @Mapping(nestedMapper = "toDto") List<Map<String, UserDto>> userMaps(List<Map<String, User>> in);
        UserDto toDto(User u);
        default UserDto special(User u) { return u == null ? null : new UserDto(u.name + "!"); }

        void replace(List<String> target, List<String> source);
        @MapperOptions(arrays = ArrayPolicy.ADD) void append(List<String> target, List<String> source);
        @MapperOptions(objects = ObjectPolicy.CLEAR_PUT) void replaceMap(Map<String, String> target, Map<String, String> source);
        @MapperOptions(objects = ObjectPolicy.PUT) void appendMap(Map<String, String> target, Map<String, String> source);
        @MapperOptions(objects = ObjectPolicy.PUT_IF_ABSENT) void putIfAbsentMap(Map<String, String> target, Map<String, String> source);

        @Mapping(target = "users", nestedMapper = "toDto")
        @Mapping(target = "map", nestedMapper = "toDto")
        @Mapping(target = "nestedUsers", nestedMapper = "toDto")
        @Mapping(target = "groupedUsers", nestedMapper = "toDto")
        DtoBox box(UserBox box);
        @Mapping(target = "users", nestedMapper = "toDto")
        @Mapping(target = "map", ignore = true)
        @Mapping(target = "nestedUsers", nestedMapper = "toDto")
        @Mapping(target = "groupedUsers", nestedMapper = "toDto")
        void updateBox(DtoBox target, UserBox box);
        @MapperOptions(nulls = NullValuePolicy.IGNORE) @Mapping(target = "users", nestedMapper = "toDto") @Mapping(target = "map", ignore = true) @Mapping(target = "nestedUsers", nestedMapper = "toDto") @Mapping(target = "groupedUsers", nestedMapper = "toDto") void ignoreNullBox(DtoBox target, UserBox box);
        @Mapping(target = "users", nestedMapper = "toDto") @Mapping(target = "map", ignore = true) @Mapping(target = "nestedUsers", nestedMapper = "toDto") @Mapping(target = "groupedUsers", nestedMapper = "toDto") void setNullBox(DtoBox target, UserBox box);
        @Mapping(target = "users", ignore = true) @Mapping(target = "map", nestedMapper = "toDto") @Mapping(target = "nestedUsers", ignore = true) @Mapping(target = "groupedUsers", ignore = true) void setNullMapBox(DtoBox target, UserBox box);
        @Mapping(target = "users", array = ArrayPolicy.ADD, nestedMapper = "toDto")
        @Mapping(target = "map", ignore = true)
        @Mapping(target = "nestedUsers", ignore = true)
        @Mapping(target = "groupedUsers", ignore = true)
        void appendBox(DtoBox target, UserBox box);
        @Mapping(target = "users", ignore = true)
        @Mapping(target = "map", nestedMapper = "toDto")
        @Mapping(target = "nestedUsers", ignore = true)
        @Mapping(target = "groupedUsers", ignore = true)
        void putBox(DtoBox target, UserBox box);
        @Mapping(target = "users", ignore = true)
        @Mapping(target = "map", object = ObjectPolicy.CLEAR_PUT, nestedMapper = "toDto")
        @Mapping(target = "nestedUsers", ignore = true)
        @Mapping(target = "groupedUsers", ignore = true)
        void clearPutBox(DtoBox target, UserBox box);
        @Mapping(target = "users", ignore = true)
        @Mapping(target = "map", object = ObjectPolicy.PUT_IF_ABSENT, nestedMapper = "toDto")
        @Mapping(target = "nestedUsers", ignore = true)
        @Mapping(target = "groupedUsers", ignore = true)
        void putIfAbsentBox(DtoBox target, UserBox box);

        @Mapping(target = "users", nestedMapper = "toDto")
        @Mapping(target = "userMap", nestedMapper = "toDto")
        @Mapping(target = "child", sources = {"child"}, compute = "this::toChildDto")
        ObntTarget obnt(ObntSource source);

        @Mapping(target = "users", nestedMapper = "toDto")
        @Mapping(target = "userMap", nestedMapper = "toDto")
        @Mapping(target = "child", sources = {"child"}, compute = "this::toChildDto")
        void updateObnt(ObntTarget target, ObntSource source);

        default ChildDto toChildDto(Child child) { return child == null ? null : new ChildDto(child.name); }
    }

    @CompiledMapper
    public interface UniqueMapper {
        List<UserDto> users(List<User> in);
        Map<String, UserDto> map(Map<String, User> in);
        UserDto toDto(User u);
    }

    @CompiledMapper
    public interface AutoNestedMapper {
        List<List<UserDto>> nestedUsers(List<List<User>> in);
        Map<String, List<UserDto>> groupedUsers(Map<String, List<User>> in);
        Map<String, Map<String, UserDto>> nestedMapUsers(Map<String, Map<String, User>> in);
        UserDto toDto(User u);
    }

    @CompiledMapper
    public interface PutIfAbsentMapper {
        int[] calls = new int[1];

        @MapperOptions(objects = ObjectPolicy.PUT_IF_ABSENT)
        @Mapping(nestedMapper = "convert")
        void update(Map<String, String> target, Map<String, String> source);

        default String convert(String value) {
            calls[0]++;
            if ("boom".equals(value)) throw new IllegalStateException("converter should have been skipped");
            return value == null ? null : value.toUpperCase(Locale.ROOT);
        }
    }

    @CompiledMapper
    public interface RecursiveUpdateMapper {
        @MapperOptions(arrays = ArrayPolicy.CLEAR_ADD, objects = ObjectPolicy.PUT)
        @Mapping(nestedMapper = "toDto")
        void putClearAddLists(Map<String, List<UserDto>> target, Map<String, List<User>> source);

        @MapperOptions(arrays = ArrayPolicy.ADD, objects = ObjectPolicy.PUT)
        @Mapping(nestedMapper = "toDto")
        void putAddLists(Map<String, List<UserDto>> target, Map<String, List<User>> source);

        @MapperOptions(objects = ObjectPolicy.PUT_IF_ABSENT)
        @Mapping(nestedMapper = "toDto")
        void putIfAbsentLists(Map<String, List<UserDto>> target, Map<String, List<User>> source);

        @MapperOptions(objects = ObjectPolicy.PUT)
        @Mapping(nestedMapper = "toDto")
        void putMaps(Map<String, Map<String, UserDto>> target, Map<String, Map<String, User>> source);

        @MapperOptions(arrays = ArrayPolicy.ADD, objects = ObjectPolicy.PUT)
        @Mapping(nestedMapper = "toDto")
        void putDeep(Map<String, Map<String, List<UserDto>>> target, Map<String, Map<String, List<User>>> source);

        @MapperOptions(objects = ObjectPolicy.CLEAR_PUT)
        @Mapping(nestedMapper = "toDto")
        void clearPutMaps(Map<String, Map<String, UserDto>> target, Map<String, Map<String, User>> source);

        UserDto toDto(User u);
    }
}
