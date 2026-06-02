package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.EnsureMapping;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MappingIfParentPresent;
import org.sjf4j.compiled.CompiledNodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MapperTargetPathContainerTest {

    @Test
    public void strictTargetPathWritesContainersAndNodes() {
        ContainerMapper mapper = CompiledNodes.of(ContainerMapper.class);

        ContainerTarget created = mapper.strictCreate(new Source("Ada"));
        assertEquals("Ada", created.map.get("name"));
        assertEquals("Ada", created.list.get(0));
        assertEquals("Ada", created.array[0]);
        assertEquals("Ada", created.object.getString("name"));
        assertEquals("Ada", created.jsonArray.getString(0));

        ContainerTarget updated = new ContainerTarget();
        mapper.strictUpdate(updated, new Source("Grace"));
        assertEquals("Grace", updated.map.get("name"));
        assertEquals("Grace", updated.list.get(0));
        assertEquals("Grace", updated.array[0]);
        assertEquals("Grace", updated.object.getString("name"));
        assertEquals("Grace", updated.jsonArray.getString(0));
    }

    @Test
    public void ifParentPresentSkipsMissingContainerParents() {
        ContainerMapper mapper = CompiledNodes.of(ContainerMapper.class);
        OptionalParents target = new OptionalParents();

        mapper.ifParentPresentMap(target, new Source("Ada"));
        mapper.ifParentPresentList(target, new Source("Ada"));
        mapper.ifParentPresentObject(target, new Source("Ada"));
        assertNull(target.map);
        assertNull(target.list);
        assertNull(target.object);

        target.map = new LinkedHashMap<>();
        target.list = new ArrayList<>(Arrays.asList((String) null));
        target.object = new JsonObject();
        mapper.ifParentPresentMap(target, new Source("Grace"));
        mapper.ifParentPresentList(target, new Source("Grace"));
        mapper.ifParentPresentObject(target, new Source("Grace"));
        assertEquals("Grace", target.map.get("name"));
        assertEquals("Grace", target.list.get(0));
        assertEquals("Grace", target.object.getString("name"));
    }

    @Test
    public void ensureMappingCreatesContainerParents() {
        ContainerMapper mapper = CompiledNodes.of(ContainerMapper.class);

        OptionalParents created = mapper.ensureCreate(new Source("Ada"));
        assertEquals("Ada", created.map.get("name"));
        mapper.ensureObjectUpdate(created, new Source("Alan"));
        assertEquals("Alan", created.object.getString("name"));

        OptionalParents updated = new OptionalParents();
        mapper.ensureMapUpdate(updated, new Source("Grace"));
        mapper.ensureObjectUpdate(updated, new Source("Hedy"));
        assertEquals("Grace", updated.map.get("name"));
        assertEquals("Hedy", updated.object.getString("name"));
    }

    public record Source(String name) {}

    public static final class ContainerTarget {
        public Map<String, String> map = new LinkedHashMap<>();
        public List<String> list = new ArrayList<>(Arrays.asList((String) null));
        public String[] array = new String[1];
        public JsonObject object = new JsonObject();
        public JsonArray jsonArray = JsonArray.of((Object) null);
    }

    public static final class OptionalParents {
        public Map<String, String> map;
        public List<String> list;
        public JsonObject object;
    }

    @CompiledMapper
    public interface ContainerMapper {
        @Mapping(target = "$.map.name", source = "name")
        @Mapping(target = "$.list[0]", source = "name")
        @Mapping(target = "$.array[0]", source = "name")
        @Mapping(target = "$.object.name", source = "name")
        @Mapping(target = "$.jsonArray[0]", source = "name")
        ContainerTarget strictCreate(Source source);

        @Mapping(target = "$.map.name", source = "name")
        @Mapping(target = "$.list[0]", source = "name")
        @Mapping(target = "$.array[0]", source = "name")
        @Mapping(target = "$.object.name", source = "name")
        @Mapping(target = "$.jsonArray[0]", source = "name")
        void strictUpdate(ContainerTarget target, Source source);

        @MappingIfParentPresent(target = "$.map.name", source = "name")
        void ifParentPresentMap(OptionalParents target, Source source);

        @MappingIfParentPresent(target = "$.list[0]", source = "name")
        void ifParentPresentList(OptionalParents target, Source source);

        @MappingIfParentPresent(target = "$.object.name", source = "name")
        void ifParentPresentObject(OptionalParents target, Source source);

        @EnsureMapping(target = "$.map.name", source = "name")
        @Mapping(target = "list", ignore = true)
        @Mapping(target = "object", ignore = true)
        OptionalParents ensureCreate(Source source);

        @EnsureMapping(target = "$.map.name", source = "name")
        void ensureMapUpdate(OptionalParents target, Source source);

        @EnsureMapping(target = "$.object.name", source = "name")
        void ensureObjectUpdate(OptionalParents target, Source source);
    }
}
