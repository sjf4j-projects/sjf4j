package org.sjf4j.testbench.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.NullValuePolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Null and absent-property regressions inspired by MapStruct's null-check and
 * null-value-property-mapping scenarios. Expectations follow SJF4J semantics.
 */
public class MapperNullSemanticsTest {

    private final NullMapper mapper = CompiledInstances.create(NullMapper.class);

    @Test
    public void nullSourceReturnsNullForCreateMethods() {
        assertNull(mapper.create((Map<String, Object>) null));
        assertNull(mapper.createIgnoringNulls(null));
        assertNull(mapper.record(null));
        assertNull(mapper.children(null));
        assertNull(mapper.dictionary(null));
    }

    @Test
    public void allNullSourcesReturnNullForCreate() {
        assertNull(mapper.combine(null, null));
    }

    @Test
    public void missingMapPropertiesKeepMutableCreateDefaults() {
        MutableTarget result = mapper.create(Map.of("name", "Ada"));
        assertEquals("Ada", result.name);
        assertEquals(7, result.count);
        assertEquals(List.of("seed"), result.labels);
    }

    @Test
    public void explicitNullMapValuesClearMutableCreateProperties() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", null);
        source.put("count", null);
        MutableTarget result = mapper.create(source);
        assertNull(result.name);
        assertNull(result.count);
    }

    @Test
    public void nullIgnorePreservesCreateDefaults() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", null);
        source.put("count", null);
        MutableTarget result = mapper.createIgnoringNulls(source);
        assertEquals("seed", result.name);
        assertEquals(7, result.count);
    }

    @Test
    public void missingMapPropertyDoesNotEraseExistingUpdateValue() {
        MutableTarget target = new MutableTarget();
        mapper.update(target, Map.of("count", 11));
        assertEquals("seed", target.name);
        assertEquals(11, target.count);
    }

    @Test
    public void explicitNullClearsExistingUpdateValueByDefault() {
        MutableTarget target = new MutableTarget();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", null);
        mapper.update(target, source);
        assertNull(target.name);
        assertEquals(7, target.count);
    }

    @Test
    public void nullIgnoreKeepsValuesAndStillUpdatesNonNullValues() {
        MutableTarget target = new MutableTarget();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", null);
        source.put("count", 12);
        mapper.updateIgnoringNulls(target, source);
        assertEquals("seed", target.name);
        assertEquals(12, target.count);
    }

    @Test
    public void nullSourceDoesNotChangeUpdateTarget() {
        MutableTarget target = new MutableTarget();
        List<String> labels = target.labels;
        mapper.update(target, null);
        assertEquals("seed", target.name);
        assertEquals(7, target.count);
        assertSame(labels, target.labels);
    }

    @Test
    public void nullableRecordPropertiesRemainNull() {
        NullableRecord record = mapper.record(new NullableSource(null, null));
        assertNotNull(record);
        assertNull(record.name());
        assertNull(record.count());
    }

    @Test
    public void nestedNullBeanPropertyMapsToNull() {
        OuterTarget result = mapper.outer(new OuterSource(null));
        assertNotNull(result);
        assertNull(result.child);
    }

    @Test
    public void emptyAssignableListUsesDirectRootAssignment() {
        List<String> source = new ArrayList<>();
        List<String> result = mapper.children(source);
        assertEquals(List.of(), result);
        assertSame(source, result);
    }

    @Test
    public void nullListElementsAndRootIdentityArePreserved() {
        List<String> source = Arrays.asList("first", null, "third");
        List<String> result = mapper.children(source);
        assertEquals(source, result);
        assertSame(source, result);
    }

    @Test
    public void emptyAssignableMapUsesDirectRootAssignment() {
        Map<String, String> source = new LinkedHashMap<>();
        Map<String, String> result = mapper.dictionary(source);
        assertEquals(Map.of(), result);
        assertSame(source, result);
    }

    public record NullableSource(String name, Integer count) {}
    public record NullableRecord(String name, Integer count) {}
    public record First(String name) {}
    public record Second(Integer count) {}
    public record OuterSource(ChildSource child) {}
    public record ChildSource(String value) {}

    public static final class MutableTarget {
        public String name = "seed";
        public Integer count = 7;
        public List<String> labels = new ArrayList<>(List.of("seed"));
    }

    public static final class OuterTarget {
        public ChildTarget child;
    }

    public static final class ChildTarget {
        public String value;
    }

    @CompiledMapper
    public interface NullMapper {
        MutableTarget create(Map<String, Object> source);

        @MappingOptions(nulls = NullValuePolicy.IGNORE)
        MutableTarget createIgnoringNulls(Map<String, Object> source);

        void update(MutableTarget target, Map<String, Object> source);

        @MappingOptions(nulls = NullValuePolicy.IGNORE)
        void updateIgnoringNulls(MutableTarget target, Map<String, Object> source);

        NullableRecord record(NullableSource source);

        OuterTarget outer(OuterSource source);

        List<String> children(List<String> source);

        Map<String, String> dictionary(Map<String, String> source);

        @Mapping(target = "name", source = "first:name")
        @Mapping(target = "count", source = "second:count")
        MutableTarget combine(First first, Second second);
    }
}
