package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MapperOptions;
import org.sjf4j.annotation.mapper.NullValuePolicy;
import org.sjf4j.compiled.CompiledNodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MapperMixedStructuralTest {
    @Test
    public void createReadsMixedStructuralSourcesAndNestedMaps() {
        MixedMapper mapper = CompiledNodes.of(MixedMapper.class);
        MixedSource source = fullSource();

        MixedTarget target = mapper.toTarget(source);

        assertEquals("meta-name", target.metadataName());
        assertEquals("object-name", target.objectName());
        assertEquals("array-zero", target.arrayFirst());
        assertEquals("list-a", target.firstItemName());
        assertEquals("array-b", target.secondArrayItemName());
        assertEquals(List.of("set-a", "set-b"), new ArrayList<>(target.set()));
        assertEquals(List.of(new ItemDto("list-a"), new ItemDto("list-b")), target.itemDtos());
        assertEquals(new ItemDto("map-a"), target.itemDtoMap().get("a"));
        assertEquals(new ItemDto("map-b"), target.itemDtoMap().get("b"));
        assertEquals(new ItemDto("child-name"), target.childDto());
        assertEquals("typed-name", target.jojoTypedName());
        assertEquals("dynamic-alias", target.jojoAlias());
        assertSame(source.jojo, target.jojoObject());
    }

    @Test
    public void createWritesTargetPathsIntoInitializedJsonContainers() {
        MixedMapper mapper = CompiledNodes.of(MixedMapper.class);

        PathTarget target = mapper.toPathTarget(fullSource());

        assertEquals("meta-name", target.out.getString("fromMap"));
        assertEquals("object-name", target.out.getString("fromJson"));
        assertEquals("array-zero", target.arrayOut.getString(0));
    }

    @Test
    public void ignoreNullsPreservesDefaultsForMissingStructuralSources() {
        MixedMapper mapper = CompiledNodes.of(MixedMapper.class);
        MixedSource source = new MixedSource();
        DefaultsTarget target = new DefaultsTarget();

        mapper.updateIgnoreNulls(target, source);

        assertEquals("default-meta", target.metadataName);
        assertEquals(new ItemDto("default-child"), target.childDto);
    }

    private static MixedSource fullSource() {
        MixedSource source = new MixedSource();
        source.metadata = new LinkedHashMap<>();
        source.metadata.put("name", "meta-name");
        source.object = JsonObject.of("name", "object-name");
        source.array = JsonArray.of("array-zero", "array-one");
        source.items = List.of(new Item("list-a"), new Item("list-b"));
        source.itemArray = new Item[] { new Item("array-a"), new Item("array-b") };
        source.set = List.of("set-a", "set-b");
        source.itemMap = new LinkedHashMap<>();
        source.itemMap.put("a", new Item("map-a"));
        source.itemMap.put("b", new Item("map-b"));
        source.child = new Item("child-name");
        source.jojo = new TypedJsonObject();
        source.jojo.setTypedName("typed-name");
        source.jojo.put("alias", "dynamic-alias");
        return source;
    }

    public record Item(String name) {}
    public record ItemDto(String name) {}

    public static class MixedSource {
        public Map<String, String> metadata;
        public JsonObject object;
        public JsonArray array;
        public List<Item> items;
        public Item[] itemArray;
        public List<String> set;
        public Map<String, Item> itemMap;
        public Item child;
        public TypedJsonObject jojo;
    }

    public record MixedTarget(
            String metadataName,
            Object objectName,
            Object arrayFirst,
            String firstItemName,
            String secondArrayItemName,
            Set<String> set,
            List<ItemDto> itemDtos,
            Map<String, ItemDto> itemDtoMap,
            ItemDto childDto,
            Object jojoTypedName,
            Object jojoAlias,
            JsonObject jojoObject) {}

    public static class PathTarget {
        public JsonObject out = new JsonObject();
        public JsonArray arrayOut = JsonArray.of(null, null);
    }

    public static class DefaultsTarget {
        public String metadataName = "default-meta";
        public ItemDto childDto = new ItemDto("default-child");
    }

    public static class TypedJsonObject extends JsonObject {
        private String typedName;

        public String getTypedName() {
            return typedName;
        }

        public void setTypedName(String typedName) {
            this.typedName = typedName;
        }
    }

    @CompiledMapper
    public interface MixedMapper {
        @Mapping(target = "metadataName", source = "$.metadata.name")
        @Mapping(target = "objectName", source = "$.object.name")
        @Mapping(target = "arrayFirst", source = "$.array[0]")
        @Mapping(target = "firstItemName", source = "$.items[0].name")
        @Mapping(target = "secondArrayItemName", source = "$.itemArray[1].name")
        @MapperOptions(using = {"toDto"})
        @Mapping(target = "set", source = "set")
        @Mapping(target = "itemDtos", source = "items")
        @Mapping(target = "itemDtoMap", source = "itemMap")
        @Mapping(target = "childDto", source = "child")
        @Mapping(target = "jojoTypedName", source = "$.jojo.typedName")
        @Mapping(target = "jojoAlias", source = "$.jojo.alias")
        @Mapping(target = "jojoObject", source = "jojo")
        MixedTarget toTarget(MixedSource source);

        @Mapping(target = "$.out.fromMap", source = "$.metadata.name")
        @Mapping(target = "$.out.fromJson", source = "$.object.name")
        @Mapping(target = "$.arrayOut[0]", source = "$.array[0]")
        PathTarget toPathTarget(MixedSource source);

        @MapperOptions(nulls = NullValuePolicy.IGNORE, using = {"toDto"})
        @Mapping(target = "metadataName", source = "$.metadata.name")
        @Mapping(target = "childDto", source = "child")
        void updateIgnoreNulls(DefaultsTarget target, MixedSource source);

        ItemDto toDto(Item item);
    }
}
