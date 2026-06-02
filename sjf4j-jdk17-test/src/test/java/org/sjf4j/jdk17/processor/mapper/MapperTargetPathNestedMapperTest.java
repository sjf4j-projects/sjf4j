package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.EnsureMapping;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MappingIfParentPresent;
import org.sjf4j.compiled.CompiledRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MapperTargetPathNestedMapperTest {

    @Test
    public void targetPathAppliesNamedNestedMapperToBeanListAndMapValues() {
        NestedPathMapper mapper = CompiledRegistry.of(NestedPathMapper.class);
        Source source = new Source(new Child("Ada"));

        Target target = mapper.create(source);
        assertEquals("dto:Ada", target.child.name);
        assertEquals("dto:Ada", target.items.get(0).name);
        assertEquals("dto:Ada", target.map.get("one").name);
    }

    @Test
    public void targetPathNestedMapperWorksWithIfParentPresentAndEnsure() {
        NestedPathMapper mapper = CompiledRegistry.of(NestedPathMapper.class);
        Source source = new Source(new Child("Grace"));

        OptionalTarget optional = new OptionalTarget();
        mapper.ifParentPresent(optional, source);
        assertNull(optional.items);

        optional.items = new ArrayList<>(Arrays.asList((ChildDto) null));
        mapper.ifParentPresent(optional, source);
        assertEquals("dto:Grace", optional.items.get(0).name);

        OptionalTarget ensured = new OptionalTarget();
        mapper.ensureMap(ensured, source);
        assertEquals("dto:Grace", ensured.map.get("one").name);
    }

    public record Source(Child child) {}
    public record Child(String name) {}

    public static final class ChildDto {
        public String name;
    }

    public static final class Target {
        public ChildDto child;
        public List<ChildDto> items = new ArrayList<>(Arrays.asList((ChildDto) null));
        public Map<String, ChildDto> map = new LinkedHashMap<>();
    }

    public static final class OptionalTarget {
        public List<ChildDto> items;
        public Map<String, ChildDto> map;
    }

    @CompiledMapper
    public interface NestedPathMapper {
        @Mapping(target = "$.child", source = "child", nestedMapper = "toDto")
        @Mapping(target = "$.items[0]", source = "child", nestedMapper = "toDto")
        @Mapping(target = "$.map.one", source = "child", nestedMapper = "toDto")
        Target create(Source source);

        @MappingIfParentPresent(target = "$.items[0]", source = "child", nestedMapper = "toDto")
        void ifParentPresent(OptionalTarget target, Source source);

        @EnsureMapping(target = "$.map.one", source = "child", nestedMapper = "toDto")
        void ensureMap(OptionalTarget target, Source source);

        default ChildDto toDto(Child child) {
            ChildDto dto = new ChildDto();
            dto.name = "dto:" + child.name();
            return dto;
        }
    }
}
