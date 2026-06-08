package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MapperOptions;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.BindingException;
import org.sjf4j.exception.JsonException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MapperArrayTest {

    @Test
    public void convertsArrayLikeSourcesToCollections() {
        ArrayMapper mapper = CompiledNodes.of(ArrayMapper.class);

        assertEquals(List.of(1, 2), mapper.jsonArrayInts(JsonArray.of(1L, 2)));
        assertEquals(new LinkedHashSet<>(List.of(Status.ACTIVE)), mapper.jsonArrayStatuses(JsonArray.of("ACTIVE")));
        assertEquals(List.of("x", "y"), mapper.jajoStrings(TypedJsonArray.ofValues("x", "y")));
        assertEquals(List.of(1L, 2L), mapper.boxedArrayLongs(new Integer[] {1, 2}));
        assertEquals(List.of(3L, 4L), mapper.primitiveArrayLongs(new int[] {3, 4}));
        assertEquals(List.of(List.of(5L), List.of(6L, 7L)), mapper.nestedPrimitiveArrayLongs(new int[][] {{5}, {6, 7}}));
        assertEquals(List.of(new UserDto("ada"), new UserDto("grace")), mapper.arrayDtos(new User[] {new User("ada"), new User("grace")}));
    }

    @Test
    public void importedMapperSupportsRootArrayUsing() {
        ImportedArrayMapper mapper = CompiledNodes.of(ImportedArrayMapper.class);

        assertEquals(List.of(new UserDto("ADA")), List.of(mapper.userDtoArray(new User[] {new User("ada")})));
    }

    @Test
    public void mapsAndUpdatesArrayLikeSourcePropertiesToCollections() {
        ArrayMapper mapper = CompiledNodes.of(ArrayMapper.class);
        ArrayLikeSource source = new ArrayLikeSource();

        ArrayLikeTarget created = mapper.arrayLike(source);
        assertEquals(List.of(1, 2), created.rawScores());
        assertEquals(List.of(3L, 4L), created.ids());
        assertEquals(List.of(7L, 8L), created.setIds());
        assertEquals(List.of(new UserDto("ada")), created.users());
        assertEquals(List.of("a", "b"), created.jajoValues());

        MutableArrayLikeTarget target = new MutableArrayLikeTarget();
        target.ids.add(99L);
        target.setIds.add(99L);
        target.rawScores.add(99);
        mapper.updateArrayLike(target, source);
        assertEquals(List.of(3L, 4L), target.ids);
        assertEquals(List.of(7L, 8L), target.setIds);
        assertEquals(List.of(1, 2), target.rawScores);
    }

    @Test
    public void updatesRootCollectionsFromArrayLikeSources() {
        ArrayMapper mapper = CompiledNodes.of(ArrayMapper.class);

        List<Long> longs = new ArrayList<>(List.of(99L));
        mapper.updateLongsFromPrimitiveArray(longs, new int[] {1, 2});
        assertEquals(List.of(1L, 2L), longs);

        List<Integer> ints = new ArrayList<>(List.of(99));
        mapper.updateIntsFromJsonArray(ints, JsonArray.of(3L, 4L));
        assertEquals(List.of(3, 4), ints);
    }

    @Test
    public void projectsArrayLikeSourcesToJsonArrayShallowly() {
        ArrayMapper mapper = CompiledNodes.of(ArrayMapper.class);
        Object nested = new User("ada");
        List<Object> list = List.of(Long.valueOf(1), nested);
        @SuppressWarnings("rawtypes") List rawList = list;
        Set<Object> set = new LinkedHashSet<>(list);
        @SuppressWarnings("rawtypes") Set rawSet = set;
        JsonArray json = JsonArray.of(Long.valueOf(2), nested);

        JsonArray fromList = mapper.jsonArrayFromList(list);
        JsonArray fromRawList = mapper.jsonArrayFromRawList(rawList);
        JsonArray fromSet = mapper.jsonArrayFromSet(set);
        JsonArray fromRawSet = mapper.jsonArrayFromRawSet(rawSet);
        JsonArray fromJson = mapper.jsonArrayFromJsonArray(json);
        JsonArray fromPrimitive = mapper.jsonArrayFromPrimitiveArray(new int[] {3, 4});

        assertEquals(Long.valueOf(1), fromList.getNode(0));
        assertSame(nested, fromList.getNode(1));
        assertEquals(Long.valueOf(1), fromRawList.getNode(0));
        assertSame(nested, fromRawList.getNode(1));
        assertEquals(Long.valueOf(1), fromSet.getNode(0));
        assertSame(nested, fromSet.getNode(1));
        assertEquals(Long.valueOf(1), fromRawSet.getNode(0));
        assertSame(nested, fromRawSet.getNode(1));
        assertNotSame(json, fromJson);
        assertEquals(Long.valueOf(2), fromJson.getNode(0));
        assertSame(nested, fromJson.getNode(1));
        assertEquals(Integer.valueOf(3), fromPrimitive.getNode(0));
        assertEquals(Integer.valueOf(4), fromPrimitive.getNode(1));

        JsonArray fromObjectList = mapper.jsonArrayFromObject(List.of(Long.valueOf(5), nested));
        assertEquals(Long.valueOf(5), fromObjectList.getNode(0));
        assertSame(nested, fromObjectList.getNode(1));
        assertThrows(BindingException.class, () -> mapper.jsonArrayFromObject(JsonArray.of(1)));
    }

    @Test
    public void mapsObjectRuntimeListOnlyToTypedCollectionsAndArrays() {
        ArrayMapper mapper = CompiledNodes.of(ArrayMapper.class);
        @SuppressWarnings("rawtypes") List rawList = List.of(Long.valueOf(9), Integer.valueOf(10));

        assertEquals(List.of(1, 2), mapper.intsFromObject(List.of(Long.valueOf(1), Integer.valueOf(2))));
        assertThrows(BindingException.class, () -> mapper.intsFromObject(Set.of("1", "2")));
        assertEquals(List.of(9, 10), mapper.integerListFromRawList(rawList));

        assertEquals(List.of(3, 4), List.of(mapper.integerArrayFromObject(List.of(Long.valueOf(3), Integer.valueOf(4)))));
        assertThrows(BindingException.class, () -> mapper.integerArrayFromObject(new int[] {3, 4}));
    }

    @Test
    public void createsJajoTargetsShallowly() {
        ArrayMapper mapper = CompiledNodes.of(ArrayMapper.class);
        Object nested = new User("ada");

        TypedJsonArray fromList = mapper.typedJsonArrayFromList(List.of(Long.valueOf(1), nested));
        TypedJsonArray fromSet = mapper.typedJsonArrayFromSet(new LinkedHashSet<>(List.of(Long.valueOf(6), nested)));
        TypedJsonArray fromJson = mapper.typedJsonArrayFromJsonArray(JsonArray.of(Long.valueOf(2), nested));
        TypedJsonArray fromPrimitive = mapper.typedJsonArrayFromPrimitiveArray(new int[] {3, 4});
        TypedJsonArray fromObject = mapper.typedJsonArrayFromObject(List.of(Long.valueOf(5), nested));

        assertEquals(TypedJsonArray.class, fromList.getClass());
        assertEquals(Long.valueOf(1), fromList.getNode(0));
        assertSame(nested, fromList.getNode(1));
        assertEquals(Long.valueOf(6), fromSet.getNode(0));
        assertSame(nested, fromSet.getNode(1));
        assertEquals(Long.valueOf(2), fromJson.getNode(0));
        assertSame(nested, fromJson.getNode(1));
        assertEquals(Integer.valueOf(3), fromPrimitive.getNode(0));
        assertEquals(Integer.valueOf(4), fromPrimitive.getNode(1));
        assertEquals(Long.valueOf(5), fromObject.getNode(0));
        assertSame(nested, fromObject.getNode(1));
    }

    @Test
    public void jajoTargetUsesRuntimeElementTypeCheck() {
        ArrayMapper mapper = CompiledNodes.of(ArrayMapper.class);

        assertThrows(JsonException.class, () -> mapper.stringJsonArray(List.of("ok", Integer.valueOf(1))));
    }

    @Test
    public void mapsArrayLikeSourcesToJavaArraysWithTypedConversion() {
        ArrayMapper mapper = CompiledNodes.of(ArrayMapper.class);
        @SuppressWarnings("rawtypes") List rawList = List.of(Long.valueOf(7), Integer.valueOf(8));

        assertEquals(List.of(1, 2), List.of(mapper.integerArray(JsonArray.of(1L, 2L))));
        assertEquals(List.of(7, 8), List.of(mapper.integerArrayFromRawList(rawList)));
        assertEquals(List.of(9, 10), List.of(mapper.integerArrayFromSet(new LinkedHashSet<>(List.of(Long.valueOf(9), Integer.valueOf(10))))));
        assertEquals(List.of(3L, 4L), List.of(mapper.longArray(new int[] {3, 4})));
        assertEquals(List.of(new UserDto("ada"), new UserDto("grace")), List.of(mapper.userDtoArray(new User[] {new User("ada"), new User("grace")})));
        assertEquals(List.of(5L, 6L), List.of(mapper.longArrayFromList(List.of(Integer.valueOf(5), Integer.valueOf(6)))));
        assertEquals(List.of(11L, 12L), mapper.longListFromSet(new LinkedHashSet<>(List.of(Integer.valueOf(11), Integer.valueOf(12)))));

        NamedArrayMapper named = CompiledNodes.of(NamedArrayMapper.class);
        assertEquals(List.of(new UserDto("ADA")), List.of(named.userDtoArray(new User[] {new User("ada")})));
    }

    public enum Status { ACTIVE }

    public record User(String name) {}
    public record UserDto(String name) {}

    public static final class TypedJsonArray extends JsonArray {
        static TypedJsonArray ofValues(Object... values) {
            TypedJsonArray array = new TypedJsonArray();
            if (values != null) for (Object value : values) array.add(value);
            return array;
        }
    }

    public static final class StringJsonArray extends JsonArray {
        @Override
        public Class<?> elementType() {
            return String.class;
        }
    }

    public static final class ArrayLikeSource {
        public JsonArray rawScores = JsonArray.of(1L, 2L);
        public int[] ids = new int[] {3, 4};
        public LinkedHashSet<Integer> setIds = new LinkedHashSet<>(List.of(7, 8));
        public User[] users = new User[] {new User("ada")};
        public TypedJsonArray jajoValues = TypedJsonArray.ofValues("a", "b");
    }

    public record ArrayLikeTarget(List<Integer> rawScores, List<Long> ids, List<Long> setIds, List<UserDto> users, List<String> jajoValues) {}

    public static final class MutableArrayLikeTarget {
        public List<Long> ids = new ArrayList<>();
        public List<Long> setIds = new ArrayList<>();
        public List<Integer> rawScores = new ArrayList<>();
    }

    @CompiledMapper
    public interface ArrayMapper {
        List<Integer> jsonArrayInts(JsonArray source);

        Set<Status> jsonArrayStatuses(JsonArray source);

        List<String> jajoStrings(TypedJsonArray source);

        List<Long> boxedArrayLongs(Integer[] source);

        List<Long> primitiveArrayLongs(int[] source);

        List<List<Long>> nestedPrimitiveArrayLongs(int[][] source);

        List<UserDto> arrayDtos(User[] source);

        JsonArray jsonArrayFromList(List<Object> source);

        JsonArray jsonArrayFromRawList(List source);

        JsonArray jsonArrayFromSet(Set<Object> source);

        JsonArray jsonArrayFromRawSet(Set source);

        JsonArray jsonArrayFromJsonArray(JsonArray source);

        JsonArray jsonArrayFromPrimitiveArray(int[] source);

        JsonArray jsonArrayFromObject(Object source);

        TypedJsonArray typedJsonArrayFromList(List<Object> source);

        TypedJsonArray typedJsonArrayFromSet(Set<Object> source);

        TypedJsonArray typedJsonArrayFromJsonArray(JsonArray source);

        TypedJsonArray typedJsonArrayFromPrimitiveArray(int[] source);

        TypedJsonArray typedJsonArrayFromObject(Object source);

        StringJsonArray stringJsonArray(List<Object> source);

        Integer[] integerArray(JsonArray source);

        Integer[] integerArrayFromRawList(List source);

        Integer[] integerArrayFromSet(Set<Object> source);

        Integer[] integerArrayFromObject(Object source);

        List<Integer> intsFromObject(Object source);

        List<Integer> integerListFromRawList(List source);

        Long[] longArray(int[] source);

        Long[] longArrayFromList(List<Integer> source);

        List<Long> longListFromSet(Set<Integer> source);

        UserDto[] userDtoArray(User[] source);

        UserDto userDto(User user);

        ArrayLikeTarget arrayLike(ArrayLikeSource source);

        void updateArrayLike(MutableArrayLikeTarget target, ArrayLikeSource source);

        void updateLongsFromPrimitiveArray(List<Long> target, int[] source);

        void updateIntsFromJsonArray(List<Integer> target, JsonArray source);
    }

    @CompiledMapper
    public interface NamedArrayMapper {
        @MapperOptions(using = {"upper"})
        UserDto[] userDtoArray(User[] source);

        default UserDto upper(User user) { return new UserDto(user.name().toUpperCase()); }

        default UserDto lower(User user) { return new UserDto(user.name().toLowerCase()); }
    }

    @CompiledMapper
    public interface ImportedArrayLeafMapper {
        default UserDto toDto(User user) { return user == null ? null : new UserDto(user.name().toUpperCase()); }
    }

    @CompiledMapper(importing = {ImportedArrayLeafMapper.class})
    public interface ImportedArrayMapper {
        @MapperOptions(using = {"ImportedArrayLeafMapper::toDto"})
        UserDto[] userDtoArray(User[] source);
    }
}
