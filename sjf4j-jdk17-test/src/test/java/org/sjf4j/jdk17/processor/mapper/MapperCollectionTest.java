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

    @Test public void beanCreateAndUpdateContainers() {
        CollectionMapper m = CompiledNodes.of(CollectionMapper.class);
        UserBox box = new UserBox();
        box.users = List.of(new User("a"));
        box.map = Map.of("b", new User("b"));
        DtoBox dto = m.box(box);
        assertEquals(new UserDto("a"), dto.users.get(0));
        assertEquals(new UserDto("b"), dto.map.get("b"));

        DtoBox target = new DtoBox();
        target.users = new ArrayList<>(List.of(new UserDto("old")));
        m.updateBox(target, box);
        assertEquals(List.of(new UserDto("a")), target.users);
        m.ignoreNullBox(target, new UserBox());
        assertEquals(List.of(new UserDto("a")), target.users);
        m.setNullBox(target, new UserBox());
        assertNull(target.users);
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
    public static class UserBox { public List<User> users; public Map<String, User> map; }
    public static class DtoBox { public List<UserDto> users; public Map<String, UserDto> map; }
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
        UserDto toDto(User u);
        default UserDto special(User u) { return u == null ? null : new UserDto(u.name + "!"); }

        void replace(List<String> target, List<String> source);
        @MapperOptions(arrays = ArrayPolicy.ADD) void append(List<String> target, List<String> source);
        @MapperOptions(objects = ObjectPolicy.CLEAR_PUT) void replaceMap(Map<String, String> target, Map<String, String> source);
        @MapperOptions(objects = ObjectPolicy.PUT) void appendMap(Map<String, String> target, Map<String, String> source);
        @MapperOptions(objects = ObjectPolicy.PUT_IF_ABSENT) void putIfAbsentMap(Map<String, String> target, Map<String, String> source);

        @Mapping(target = "users", nestedMapper = "toDto")
        @Mapping(target = "map", nestedMapper = "toDto")
        DtoBox box(UserBox box);
        @Mapping(target = "users", nestedMapper = "toDto")
        @Mapping(target = "map", ignore = true)
        void updateBox(DtoBox target, UserBox box);
        @MapperOptions(nulls = NullValuePolicy.IGNORE) @Mapping(target = "users", nestedMapper = "toDto") @Mapping(target = "map", ignore = true) void ignoreNullBox(DtoBox target, UserBox box);
        @Mapping(target = "users", nestedMapper = "toDto") @Mapping(target = "map", ignore = true) void setNullBox(DtoBox target, UserBox box);
        @Mapping(target = "users", array = ArrayPolicy.ADD, nestedMapper = "toDto")
        @Mapping(target = "map", ignore = true)
        void appendBox(DtoBox target, UserBox box);
        @Mapping(target = "users", ignore = true)
        @Mapping(target = "map", nestedMapper = "toDto")
        void putBox(DtoBox target, UserBox box);
        @Mapping(target = "users", ignore = true)
        @Mapping(target = "map", object = ObjectPolicy.CLEAR_PUT, nestedMapper = "toDto")
        void clearPutBox(DtoBox target, UserBox box);
        @Mapping(target = "users", ignore = true)
        @Mapping(target = "map", object = ObjectPolicy.PUT_IF_ABSENT, nestedMapper = "toDto")
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
}
